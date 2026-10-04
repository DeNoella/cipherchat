package com.cipherchat.service;

import com.cipherchat.config.AppProperties;
import com.cipherchat.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.Ciphertext;
import org.springframework.vault.support.Plaintext;
import org.springframework.vault.support.VaultTransitContext;

import java.nio.charset.StandardCharsets;

/**
 * Envelope encryption for key backups with Vault Transit. The backup arrives already locked with
 * the user's passphrase; this adds a second lock whose key never leaves Vault, so a stolen database
 * dump is useless on its own. Unwrapping only removes this outer lock: what comes back is still the
 * passphrase-locked key, never a usable private key.
 *
 * <p>The Transit key is "derived": each user's backup is encrypted under a key derived from their
 * user ID, so a ciphertext copied onto another user's row cannot be decrypted.
 */
@Component
public class KeyBackupEnvelope {

    private static final Logger log = LoggerFactory.getLogger(KeyBackupEnvelope.class);
    private static final String UNAVAILABLE = "Key backup storage is unavailable. Try again in a moment.";

    private final VaultTransitOperations transit;
    private final String keyName;

    public KeyBackupEnvelope(VaultOperations vault, AppProperties properties) {
        this.transit = vault.opsForTransit(properties.keyBackup().transitPath());
        this.keyName = properties.keyBackup().transitKey();
    }

    /** Returns Vault ciphertext ("vault:v1:..."), bound to this user. */
    public String wrap(long userId, String passphraseLockedBackup) {
        Plaintext plaintext = Plaintext.of(passphraseLockedBackup.getBytes(StandardCharsets.US_ASCII))
                .with(context(userId));
        try {
            return transit.encrypt(keyName, plaintext).getCiphertext();
        } catch (VaultException e) {
            log.error("Vault Transit encrypt failed for user {}", userId, e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
    }

    /** Removes the Vault layer. The result is still locked with the user's passphrase. */
    public String unwrap(long userId, String vaultCiphertext) {
        try {
            Plaintext plaintext = transit.decrypt(keyName, Ciphertext.of(vaultCiphertext).with(context(userId)));
            return plaintext.asString(StandardCharsets.US_ASCII);
        } catch (VaultException e) {
            log.error("Vault Transit decrypt failed for user {}", userId, e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
    }

    private static VaultTransitContext context(long userId) {
        return VaultTransitContext.builder()
                .context(("cipherchat-user:" + userId).getBytes(StandardCharsets.US_ASCII))
                .build();
    }
}
