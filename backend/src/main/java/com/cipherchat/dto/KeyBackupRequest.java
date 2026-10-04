package com.cipherchat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KeyBackupRequest(
        @NotBlank @Size(max = 20000, message = "is too large")
        @Schema(description = "ASCII-armored OpenPGP private key, locked with the user's passphrase in the browser")
        String keyBackup) {
}
