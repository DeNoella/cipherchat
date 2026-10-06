package com.cipherchat.service;

import com.cipherchat.dto.KeyResponse;
import com.cipherchat.dto.PublicKeyInfo;
import com.cipherchat.exception.ApiException;
import com.cipherchat.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Set;

@Service
public class PublicKeyService {

    private final OpenPgpInspector inspector;
    private final UserService userService;

    public PublicKeyService(OpenPgpInspector inspector, UserService userService) {
        this.inspector = inspector;
        this.userService = userService;
    }

    /** Validates the key with BouncyCastle and stores its canonical form and fingerprint on the user. */
    public PublicKeyInfo applyTo(User user, String armoredPublicKey) {
        PublicKeyInfo info = inspector.inspectPublicKey(armoredPublicKey);
        user.setPublicKey(info.armored(), info.fingerprint(), info.algorithm(), info.createdAt());
        return info;
    }

    @Transactional
    public KeyResponse upload(Long userId, String armoredPublicKey) {
        User user = userService.getById(userId);
        applyTo(user, armoredPublicKey);
        return KeyResponse.from(user);
    }

    @Transactional(readOnly = true)
    public KeyResponse get(String username) {
        User user = userService.getByUsername(username);
        if (!user.hasPublicKey()) {
            throw ApiException.notFound("User has not uploaded a public key yet");
        }
        return KeyResponse.from(user);
    }

    /**
     * Enforces the E2EE contract server-side: ciphertext must be addressed to the recipient's
     * current key (so they can read it) and to the sender's key (so the sender can read their history).
     */
    public void requireAddressedToBoth(Set<Long> recipientKeyIds, User sender, User recipient) {
        if (!sender.hasPublicKey()) {
            throw ApiException.badRequest("Upload your public key before sending messages");
        }
        if (!recipient.hasPublicKey()) {
            throw ApiException.badRequest("Recipient has not uploaded a public key yet");
        }
        Set<Long> recipientKeys = inspector.inspectPublicKey(recipient.getPublicKey()).encryptionKeyIds();
        Set<Long> senderKeys = inspector.inspectPublicKey(sender.getPublicKey()).encryptionKeyIds();
        if (Collections.disjoint(recipientKeyIds, recipientKeys)) {
            throw ApiException.badRequest("Message is not encrypted to the recipient's current public key");
        }
        if (Collections.disjoint(recipientKeyIds, senderKeys)) {
            throw ApiException.badRequest("Message must also be encrypted to your own public key");
        }
    }
}
