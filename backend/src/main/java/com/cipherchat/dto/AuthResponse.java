package com.cipherchat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
        String token,
        @Schema(example = "Bearer") String tokenType,
        long expiresIn,
        String username,
        boolean hasPublicKey,
        String fingerprint,
        @Schema(description = "True if a passphrase-locked key backup is stored, so a new device can be set up")
        boolean hasKeyBackup) {
}
