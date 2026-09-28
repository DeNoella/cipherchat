package com.cipherchat.crypto;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.ECPublicBCPGKey;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPMarker;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-side OpenPGP checks with BouncyCastle. The server holds no private keys, so it can
 * only inspect structure and signatures on public data. It never decrypts anything.
 */
@Component
public class OpenPgpInspector {

    private static final int MIN_RSA_BITS = 2048;
    private static final String PUBLIC_KEY_HEADER = "-----BEGIN PGP PUBLIC KEY BLOCK-----";

    private static final Map<String, String> CURVE_NAMES = Map.of(
            "1.3.6.1.4.1.11591.15.1", "Ed25519",
            "1.3.6.1.4.1.3029.1.5.1", "Curve25519",
            "1.2.840.10045.3.1.7", "P-256",
            "1.3.132.0.34", "P-384",
            "1.3.132.0.35", "P-521");

    private final Clock clock;

    public OpenPgpInspector(Clock clock) {
        this.clock = clock;
    }

    /**
     * Parses and validates an ASCII-armored public key. Rejects private keys, multiple keys,
     * bad self-signatures, revoked or expired keys, keys without an encryption subkey and weak RSA.
     */
    public PublicKeyInfo inspectPublicKey(String armored) {
        if (armored == null || !armored.contains(PUBLIC_KEY_HEADER)) {
            if (armored != null && armored.contains("PRIVATE KEY BLOCK")) {
                throw new InvalidPgpDataException("That is a PRIVATE key. Never upload it; upload your public key only");
            }
            throw new InvalidPgpDataException("Expected an ASCII-armored OpenPGP public key block");
        }
        // The armor decoder only reads the first block, so catch concatenated blocks explicitly.
        if (armored.indexOf(PUBLIC_KEY_HEADER) != armored.lastIndexOf(PUBLIC_KEY_HEADER)) {
            throw new InvalidPgpDataException("Upload exactly one public key");
        }

        PGPPublicKeyRing ring = readSingleRing(armored);
        PGPPublicKey primary = ring.getPublicKey();
        Instant now = clock.instant();

        if (!primary.isMasterKey()) {
            throw new InvalidPgpDataException("Key block does not start with a primary key");
        }
        if (primary.hasRevocation()) {
            throw new InvalidPgpDataException("Key has been revoked");
        }
        rejectWeak(primary);
        Instant expiresAt = expiry(primary);
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new InvalidPgpDataException("Key has expired");
        }

        List<String> userIds = verifiedUserIds(primary);
        if (userIds.isEmpty()) {
            throw new InvalidPgpDataException("Key has no user ID with a valid self-signature");
        }

        Set<Long> encryptionKeyIds = new HashSet<>();
        Iterator<PGPPublicKey> keys = ring.getPublicKeys();
        while (keys.hasNext()) {
            PGPPublicKey key = keys.next();
            if (!key.isEncryptionKey() || key.hasRevocation()) {
                continue;
            }
            Instant subExpiry = expiry(key);
            if (subExpiry != null && !subExpiry.isAfter(now)) {
                continue;
            }
            if (key.isMasterKey() || hasValidBinding(primary, key)) {
                rejectWeak(key);
                encryptionKeyIds.add(key.getKeyID());
            }
        }
        if (encryptionKeyIds.isEmpty()) {
            throw new InvalidPgpDataException("Key has no valid encryption subkey");
        }

        return new PublicKeyInfo(
                armor(ring),
                Hex.toHexString(primary.getFingerprint()).toUpperCase(),
                describe(ring),
                primary.getCreationTime().toInstant(),
                expiresAt,
                userIds,
                Set.copyOf(encryptionKeyIds));
    }

    /**
     * Confirms the data is an OpenPGP public-key-encrypted message and returns the key IDs it
     * is addressed to. Only the packet headers are read, not the encrypted payload.
     */
    public Set<Long> encryptedMessageRecipients(InputStream data) {
        try (InputStream in = PGPUtil.getDecoderStream(data)) {
            BcPGPObjectFactory factory = new BcPGPObjectFactory(in);
            Object first = factory.nextObject();
            while (first instanceof PGPMarker) {
                first = factory.nextObject();
            }
            if (!(first instanceof PGPEncryptedDataList list) || list.isEmpty()) {
                throw new InvalidPgpDataException("Content must be an OpenPGP encrypted message");
            }
            Set<Long> recipients = new HashSet<>();
            for (Object entry : list) {
                if (entry instanceof PGPPublicKeyEncryptedData pked) {
                    recipients.add(pked.getKeyID());
                }
            }
            if (recipients.isEmpty()) {
                throw new InvalidPgpDataException("Message is not encrypted to any public key");
            }
            return recipients;
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidPgpDataException ipe) {
                throw ipe;
            }
            throw new InvalidPgpDataException("Content must be an OpenPGP encrypted message");
        }
    }

    public Set<Long> encryptedMessageRecipients(String armored) {
        if (armored == null || !armored.stripLeading().startsWith("-----BEGIN PGP MESSAGE-----")) {
            throw new InvalidPgpDataException("Ciphertext must be an ASCII-armored OpenPGP message");
        }
        return encryptedMessageRecipients(new ByteArrayInputStream(armored.getBytes(StandardCharsets.US_ASCII)));
    }

    private static PGPPublicKeyRing readSingleRing(String armored) {
        try (InputStream in = PGPUtil.getDecoderStream(
                new ByteArrayInputStream(armored.getBytes(StandardCharsets.US_ASCII)))) {
            PGPPublicKeyRingCollection rings = new PGPPublicKeyRingCollection(in, new BcKeyFingerprintCalculator());
            if (rings.size() != 1) {
                throw new InvalidPgpDataException("Upload exactly one public key (found " + rings.size() + ")");
            }
            return rings.getKeyRings().next();
        } catch (IOException | PGPException | RuntimeException e) {
            if (e instanceof InvalidPgpDataException ipe) {
                throw ipe;
            }
            throw new InvalidPgpDataException("Malformed OpenPGP public key");
        }
    }

    private static List<String> verifiedUserIds(PGPPublicKey primary) {
        List<String> verified = new ArrayList<>();
        Iterator<byte[]> ids = primary.getRawUserIDs();
        while (ids.hasNext()) {
            byte[] rawId = ids.next();
            Iterator<PGPSignature> sigs = primary.getSignaturesForID(rawId);
            while (sigs != null && sigs.hasNext()) {
                PGPSignature sig = sigs.next();
                if (sig.getKeyID() != primary.getKeyID() || !sig.isCertification()) {
                    continue;
                }
                try {
                    sig.init(new BcPGPContentVerifierBuilderProvider(), primary);
                    if (sig.verifyCertification(rawId, primary)) {
                        verified.add(new String(rawId, StandardCharsets.UTF_8));
                        break;
                    }
                } catch (PGPException | RuntimeException ignored) {
                    // Treat unverifiable signatures as absent.
                }
            }
        }
        return verified;
    }

    private static boolean hasValidBinding(PGPPublicKey primary, PGPPublicKey subkey) {
        Iterator<PGPSignature> sigs = subkey.getSignaturesOfType(PGPSignature.SUBKEY_BINDING);
        while (sigs.hasNext()) {
            PGPSignature sig = sigs.next();
            if (sig.getKeyID() != primary.getKeyID()) {
                continue;
            }
            try {
                sig.init(new BcPGPContentVerifierBuilderProvider(), primary);
                if (sig.verifyCertification(primary, subkey)) {
                    return true;
                }
            } catch (PGPException | RuntimeException ignored) {
                // Fall through to the next signature.
            }
        }
        return false;
    }

    private static Instant expiry(PGPPublicKey key) {
        long validSeconds = key.getValidSeconds();
        return validSeconds > 0 ? key.getCreationTime().toInstant().plusSeconds(validSeconds) : null;
    }

    private static void rejectWeak(PGPPublicKey key) {
        int alg = key.getAlgorithm();
        boolean rsa = alg == PublicKeyAlgorithmTags.RSA_GENERAL || alg == PublicKeyAlgorithmTags.RSA_ENCRYPT
                || alg == PublicKeyAlgorithmTags.RSA_SIGN;
        if (rsa && key.getBitStrength() < MIN_RSA_BITS) {
            throw new InvalidPgpDataException("RSA keys must be at least " + MIN_RSA_BITS + " bits");
        }
        if (alg == PublicKeyAlgorithmTags.DSA || alg == PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT
                || alg == PublicKeyAlgorithmTags.ELGAMAL_GENERAL) {
            throw new InvalidPgpDataException("DSA/ElGamal keys are not supported; use Curve25519 or RSA");
        }
    }

    private static String describe(PGPPublicKeyRing ring) {
        List<String> parts = new ArrayList<>();
        ring.getPublicKeys().forEachRemaining(k -> parts.add(algorithmName(k)));
        return String.join(" + ", parts);
    }

    private static String algorithmName(PGPPublicKey key) {
        String base = switch (key.getAlgorithm()) {
            case PublicKeyAlgorithmTags.RSA_GENERAL, PublicKeyAlgorithmTags.RSA_ENCRYPT,
                 PublicKeyAlgorithmTags.RSA_SIGN -> "RSA-" + key.getBitStrength();
            case PublicKeyAlgorithmTags.EDDSA_LEGACY -> "EdDSA";
            case PublicKeyAlgorithmTags.ECDH -> "ECDH";
            case PublicKeyAlgorithmTags.ECDSA -> "ECDSA";
            case PublicKeyAlgorithmTags.Ed25519 -> "Ed25519";
            case PublicKeyAlgorithmTags.Ed448 -> "Ed448";
            case PublicKeyAlgorithmTags.X25519 -> "X25519";
            case PublicKeyAlgorithmTags.X448 -> "X448";
            default -> "alg-" + key.getAlgorithm();
        };
        if (key.getPublicKeyPacket().getKey() instanceof ECPublicBCPGKey ec) {
            String curve = CURVE_NAMES.getOrDefault(ec.getCurveOID().getId(), ec.getCurveOID().getId());
            return base + "/" + curve;
        }
        return base;
    }

    private static String armor(PGPPublicKeyRing ring) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ArmoredOutputStream armoredOut = ArmoredOutputStream.builder().clearHeaders().build(out)) {
                ring.encode(armoredOut, true);
            }
            return out.toString(StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new InvalidPgpDataException("Could not re-encode public key");
        }
    }
}
