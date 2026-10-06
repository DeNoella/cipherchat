package com.cipherchat.service;

import com.cipherchat.dto.KeyBackupResponse;
import com.cipherchat.exception.ApiException;
import com.cipherchat.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores each user's passphrase-locked private key backup. The server cannot open the backup:
 * it only checks that it is locked and belongs to the account's public key, then wraps it with
 * Vault Transit ({@link KeyBackupEnvelope}) before it reaches the database.
 */
@Service
public class KeyBackupService {

    private final OpenPgpInspector inspector;
    private final KeyBackupEnvelope envelope;
    private final UserService userService;

    public KeyBackupService(OpenPgpInspector inspector, KeyBackupEnvelope envelope, UserService userService) {
        this.inspector = inspector;
        this.envelope = envelope;
        this.userService = userService;
    }

    /** Checks the backup is passphrase-locked and matches the user's current public key. */
    public void validate(User user, String armoredBackup) {
        if (!user.hasPublicKey()) {
            throw ApiException.badRequest("Upload your public key before its backup");
        }
        String fingerprint = inspector.inspectKeyBackup(armoredBackup);
        if (!fingerprint.equals(user.getKeyFingerprint())) {
            throw ApiException.badRequest("Key backup does not match your public key");
        }
    }

    /** Wraps a validated backup with Vault Transit and attaches it. The user must already have an ID. */
    public void store(User user, String armoredBackup) {
        user.setKeyBackup(envelope.wrap(user.getId(), armoredBackup));
    }

    @Transactional
    public KeyBackupResponse upload(Long userId, String armoredBackup) {
        User user = userService.getById(userId);
        validate(user, armoredBackup);
        store(user, armoredBackup);
        return new KeyBackupResponse(user.getKeyFingerprint(), armoredBackup, user.getKeyBackupUpdatedAt());
    }

    @Transactional(readOnly = true)
    public KeyBackupResponse get(Long userId) {
        User user = userService.getById(userId);
        if (!user.hasKeyBackup()) {
            throw ApiException.notFound("No key backup is stored for this account");
        }
        String armoredBackup = envelope.unwrap(user.getId(), user.getKeyBackup());
        return new KeyBackupResponse(user.getKeyFingerprint(), armoredBackup, user.getKeyBackupUpdatedAt());
    }
}
