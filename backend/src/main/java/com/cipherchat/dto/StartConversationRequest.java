package com.cipherchat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartConversationRequest(@NotBlank @Size(max = 32) String username) {
}
