package com.cipherchat.dto;

import com.cipherchat.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "The signed-in user's own account. Never contains the password hash or the key backup.")
public record UserProfileResponse(
        String username,
        Instant createdAt,
        boolean hasPublicKey,
        @Schema(nullable = true) String fingerprint,
        @Schema(nullable = true) String keyAlgorithm,
        @Schema(nullable = true) Instant keyUploadedAt,
        boolean hasKeyBackup) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getUsername(), user.getCreatedAt(), user.hasPublicKey(),
                user.getKeyFingerprint(), user.getKeyAlgorithm(), user.getKeyUploadedAt(), user.hasKeyBackup());
    }
}
