package com.cipherchat.controller;

import com.cipherchat.dto.ConversationResponse;
import com.cipherchat.dto.MessageResponse;
import com.cipherchat.dto.StartConversationRequest;
import com.cipherchat.security.AuthUser;
import com.cipherchat.service.ConversationService;
import com.cipherchat.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Conversations", description = "One-to-one conversations and their encrypted history")
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    public ConversationController(ConversationService conversationService, MessageService messageService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    @Operation(summary = "List your conversations, most recent activity first")
    @GetMapping
    public List<ConversationResponse> list(@AuthenticationPrincipal AuthUser me) {
        return conversationService.listFor(me.id());
    }

    @Operation(summary = "Open (get or create) a conversation with another user")
    @PostMapping
    public ConversationResponse start(@AuthenticationPrincipal AuthUser me,
                                      @Valid @RequestBody StartConversationRequest request) {
        return conversationService.start(me.id(), request.username());
    }

    @Operation(summary = "Encrypted message history, oldest first. Page backwards with 'before'.")
    @GetMapping("/{id}/messages")
    public List<MessageResponse> messages(@AuthenticationPrincipal AuthUser me,
                                          @PathVariable Long id,
                                          @Parameter(description = "Return messages with id lower than this")
                                          @RequestParam(required = false) Long before,
                                          @RequestParam(defaultValue = "50") int size) {
        return messageService.history(id, me.id(), before, size);
    }
}
