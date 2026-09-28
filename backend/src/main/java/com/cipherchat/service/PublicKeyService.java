package com.cipherchat.service;

import com.cipherchat.dto.PublicKeyInfo;
import com.cipherchat.model.User;
import org.springframework.stereotype.Service;

@Service
public class PublicKeyService {

    private final OpenPgpInspector inspector;

    public PublicKeyService(OpenPgpInspector inspector) {
        this.inspector = inspector;
    }

    /** Validates the key with BouncyCastle and stores its canonical form and fingerprint on the user. */
    public PublicKeyInfo applyTo(User user, String armoredPublicKey) {
        PublicKeyInfo info = inspector.inspectPublicKey(armoredPublicKey);
        user.setPublicKey(info.armored(), info.fingerprint(), info.algorithm(), info.createdAt());
        return info;
    }
}
