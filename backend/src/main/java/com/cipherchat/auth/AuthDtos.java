package com.cipherchat.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    public static final String USERNAME_REGEX = "^[A-Za-z0-9_]{3,32}$";

    private AuthDtos() {
    }

    @Schema(description = "New account. publicKey is optional here and can be uploaded later via PUT /api/keys/me.")
    public record RegisterRequest(
            @NotBlank @Pattern(regexp = USERNAME_REGEX, message = "3-32 characters: letters, digits or underscore")
            String username,
            @NotBlank @Size(min = 10, max = 72, message = "must be 10-72 characters")
            String password,
            @Size(max = 20000, message = "is too large")
            @Schema(description = "ASCII-armored OpenPGP public key", nullable = true)
            String publicKey) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 32) String username,
            @NotBlank @Size(max = 72) String password) {
    }

    public record AuthResponse(
            String token,
            @Schema(example = "Bearer") String tokenType,
            long expiresIn,
            String username,
            boolean hasPublicKey,
            String fingerprint) {
    }
}
