package com.cipherchat.dto;

import com.cipherchat.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record KeyResponse(
        String username,
        String publicKey,
        @Schema(description = "Uppercase hex fingerprint of the primary key, for out-of-band verification")
        String fingerprint,
        String algorithm,
        Instant keyCreatedAt,
        Instant uploadedAt) {

    public static KeyResponse from(User user) {
        return new KeyResponse(user.getUsername(), user.getPublicKey(), user.getKeyFingerprint(),
                user.getKeyAlgorithm(), user.getKeyCreatedAt(), user.getKeyUploadedAt());
    }
}
