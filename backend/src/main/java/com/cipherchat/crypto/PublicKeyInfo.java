package com.cipherchat.crypto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Facts the server extracted from an uploaded OpenPGP public key.
 *
 * @param armored          canonical re-armored encoding (junk headers and trailing data stripped)
 * @param fingerprint      uppercase hex fingerprint of the primary key
 * @param encryptionKeyIds key IDs of usable encryption (sub)keys
 */
public record PublicKeyInfo(
        String armored,
        String fingerprint,
        String algorithm,
        Instant createdAt,
        Instant expiresAt,
        List<String> userIds,
        Set<Long> encryptionKeyIds) {
}
