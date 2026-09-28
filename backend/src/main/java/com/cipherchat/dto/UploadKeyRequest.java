package com.cipherchat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UploadKeyRequest(
        @NotBlank @Size(max = 20000, message = "is too large")
        @Schema(description = "ASCII-armored OpenPGP public key (never the private key)")
        String publicKey) {
}
