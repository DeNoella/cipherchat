package com.cipherchat.dto;

import com.cipherchat.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "New account. publicKey is optional here and can be uploaded later via PUT /api/keys/me.")
public record RegisterRequest(
        @NotBlank @Pattern(regexp = User.USERNAME_REGEX, message = "3-32 characters: letters, digits or underscore")
        String username,
        @NotBlank @Size(min = 10, max = 72, message = "must be 10-72 characters")
        String password,
        @Size(max = 20000, message = "is too large")
        @Schema(description = "ASCII-armored OpenPGP public key", nullable = true)
        String publicKey) {
}
