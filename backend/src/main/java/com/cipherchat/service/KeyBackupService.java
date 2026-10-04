package com.cipherchat.service;

import com.cipherchat.dto.KeyBackupResponse;
import com.cipherchat.exception.ApiException;
import com.cipherchat.model.User;
import com.cipherchat.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores each user's passphrase-locked private key backup. The server cannot open the backup:
 * it only checks that it is locked and belongs to the account's public key.
 */
@Service
public class KeyBackupService {

    private final OpenPgpInspector inspector;
    private final UserRepository users;

    public KeyBackupService(OpenPgpInspector inspector, UserRepository users) {
        this.inspector = inspector;
        this.users = users;
    }

    /** Validates the backup against the user's current public key and attaches it. */
    public void applyTo(User user, String armoredBackup) {
        if (!user.hasPublicKey()) {
            throw ApiException.badRequest("Upload your public key before its backup");
        }
        String fingerprint = inspector.inspectKeyBackup(armoredBackup);
        if (!fingerprint.equals(user.getKeyFingerprint())) {
            throw ApiException.badRequest("Key backup does not match your public key");
        }
        user.setKeyBackup(armoredBackup);
    }

    @Transactional
    public KeyBackupResponse upload(Long userId, String armoredBackup) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        applyTo(user, armoredBackup);
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public KeyBackupResponse get(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        if (!user.hasKeyBackup()) {
            throw ApiException.notFound("No key backup is stored for this account");
        }
        return toResponse(user);
    }

    private KeyBackupResponse toResponse(User user) {
        return new KeyBackupResponse(user.getKeyFingerprint(), user.getKeyBackup(), user.getKeyBackupUpdatedAt());
    }
}
