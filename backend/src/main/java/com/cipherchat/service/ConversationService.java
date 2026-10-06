package com.cipherchat.service;

import com.cipherchat.dto.ConversationResponse;
import com.cipherchat.exception.ApiException;
import com.cipherchat.model.Conversation;
import com.cipherchat.model.User;
import com.cipherchat.repository.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConversationService {

    private final ConversationRepository conversations;
    private final UserService userService;

    public ConversationService(ConversationRepository conversations, UserService userService) {
        this.conversations = conversations;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listFor(Long userId) {
        return conversations.findAllForUser(userId).stream()
                .map(c -> ConversationResponse.from(c, userId))
                .toList();
    }

    @Transactional
    public ConversationResponse start(Long userId, String peerUsername) {
        return ConversationResponse.from(getOrCreate(userId, peerUsername), userId);
    }

    /** Finds the one-to-one conversation between the caller and {@code peerUsername}, creating it if needed. */
    @Transactional
    public Conversation getOrCreate(Long userId, String peerUsername) {
        User me = userService.getById(userId);
        User peer = userService.getByUsername(peerUsername);
        if (me.getId().equals(peer.getId())) {
            throw ApiException.badRequest("You cannot start a conversation with yourself");
        }
        long low = Math.min(me.getId(), peer.getId());
        long high = Math.max(me.getId(), peer.getId());
        // A concurrent insert of the same pair hits the unique constraint and surfaces as 409.
        return conversations.findPair(low, high)
                .orElseGet(() -> conversations.saveAndFlush(Conversation.between(me, peer)));
    }

    /**
     * Loads a conversation the caller participates in. Non-participants get 404 rather than 403
     * so conversation ids cannot be probed.
     */
    @Transactional(readOnly = true)
    public Conversation requireParticipant(Long conversationId, Long userId) {
        return conversations.findById(conversationId)
                .filter(c -> c.hasParticipant(userId))
                .orElseThrow(() -> ApiException.notFound("Conversation not found"));
    }
}
