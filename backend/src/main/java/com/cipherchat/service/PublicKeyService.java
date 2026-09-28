package com.cipherchat.service;

import com.cipherchat.dto.KeyResponse;
import com.cipherchat.dto.PublicKeyInfo;
import com.cipherchat.exception.ApiException;
import com.cipherchat.model.User;
import com.cipherchat.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicKeyService {

    private final OpenPgpInspector inspector;
    private final UserRepository users;

    public PublicKeyService(OpenPgpInspector inspector, UserRepository users) {
        this.inspector = inspector;
        this.users = users;
    }

    /** Validates the key with BouncyCastle and stores its canonical form and fingerprint on the user. */
    public PublicKeyInfo applyTo(User user, String armoredPublicKey) {
        PublicKeyInfo info = inspector.inspectPublicKey(armoredPublicKey);
        user.setPublicKey(info.armored(), info.fingerprint(), info.algorithm(), info.createdAt());
        return info;
    }

    @Transactional
    public KeyResponse upload(Long userId, String armoredPublicKey) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        applyTo(user, armoredPublicKey);
        return KeyResponse.from(user);
    }

    @Transactional(readOnly = true)
    public KeyResponse get(String username) {
        User user = users.findByUsername(AuthService.normalizeUsername(username))
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (!user.hasPublicKey()) {
            throw ApiException.notFound("User has not uploaded a public key yet");
        }
        return KeyResponse.from(user);
    }
}
