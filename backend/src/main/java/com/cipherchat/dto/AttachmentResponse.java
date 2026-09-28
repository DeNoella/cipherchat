package com.cipherchat.dto;

import com.cipherchat.model.Attachment;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(UUID id, Long conversationId, long sizeBytes, Instant createdAt) {

    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(attachment.getId(), attachment.getConversation().getId(),
                attachment.getSizeBytes(), attachment.getCreatedAt());
    }
}
