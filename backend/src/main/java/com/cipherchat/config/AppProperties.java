package com.cipherchat.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Attachments attachments,
        @Valid @NotNull Messages messages,
        @Valid @NotNull LoginRateLimit loginRateLimit) {

    public record Jwt(
            @NotBlank(message = "JWT_SECRET must be set") String secret,
            @NotNull Duration ttl,
            @NotBlank String issuer) {
    }

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    public record Attachments(@NotBlank String dir, @Min(1) long maxEncryptedBytes) {
    }

    public record Messages(@Min(1) int maxCiphertextChars) {
    }

    public record LoginRateLimit(@Min(1) int maxAttempts, @Min(1) int maxAttemptsPerIp, @NotNull Duration window) {
    }
}
