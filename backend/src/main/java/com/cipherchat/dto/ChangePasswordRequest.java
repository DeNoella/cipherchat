package com.cipherchat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Change the login password. The key passphrase is separate and is not affected.")
public record ChangePasswordRequest(
        @NotBlank
        String currentPassword,
        @NotBlank @Size(min = 10, max = 72, message = "must be 10-72 characters")
        String newPassword) {
}
