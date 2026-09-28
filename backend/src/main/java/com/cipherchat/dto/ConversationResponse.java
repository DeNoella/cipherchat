package com.cipherchat.dto;

import com.cipherchat.model.Conversation;
import com.cipherchat.model.User;

import java.time.Instant;

public record ConversationResponse(
        Long id,
        UserSummary peer,
        Instant createdAt,
        Instant lastMessageAt) {

    public static ConversationResponse from(Conversation conversation, Long viewerId) {
        User peer = conversation.peerOf(viewerId);
        return new ConversationResponse(conversation.getId(), UserSummary.from(peer),
                conversation.getCreatedAt(), conversation.getLastMessageAt());
    }
}
