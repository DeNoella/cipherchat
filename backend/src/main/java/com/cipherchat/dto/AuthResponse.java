package com.cipherchat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
        String token,
        @Schema(example = "Bearer") String tokenType,
        long expiresIn,
        String username,
        boolean hasPublicKey,
        String fingerprint) {
}
