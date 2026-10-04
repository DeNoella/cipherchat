package com.cipherchat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "The passphrase-locked private key backup. Only the owner's browser can unlock it.")
public record KeyBackupResponse(
        @Schema(description = "Fingerprint of the key in the backup (matches the account's public key)")
        String fingerprint,
        @Schema(description = "ASCII-armored OpenPGP private key, still locked with the user's passphrase")
        String keyBackup,
        Instant updatedAt) {
}
