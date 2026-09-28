package com.cipherchat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendMessageRequest(
        @NotBlank @Size(max = 32) String recipientUsername,
        @NotBlank
        @Schema(description = "ASCII-armored OpenPGP message encrypted to BOTH recipient and sender keys and signed "
                + "by the sender. Plaintext is rejected.")
        String ciphertext,
        @Schema(description = "Optional id returned by POST /api/attachments", nullable = true)
        UUID attachmentId) {
}
