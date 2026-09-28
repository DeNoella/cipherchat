package com.cipherchat.dto;

import com.cipherchat.model.Message;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        Long id,
        Long conversationId,
        String sender,
        String ciphertext,
        UUID attachmentId,
        Instant createdAt) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getUsername(),
                message.getCiphertext(),
                message.getAttachment() != null ? message.getAttachment().getId() : null,
                message.getCreatedAt());
    }
}
