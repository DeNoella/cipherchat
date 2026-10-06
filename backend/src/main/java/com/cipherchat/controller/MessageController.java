package com.cipherchat.controller;

import com.cipherchat.dto.MessageResponse;
import com.cipherchat.dto.SendMessageRequest;
import com.cipherchat.security.AuthUser;
import com.cipherchat.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Messages", description = "Store and relay OpenPGP ciphertext")
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @Operation(summary = "Send an encrypted message. The server verifies it is OpenPGP ciphertext "
            + "addressed to both participants, stores it, and pushes it over WebSocket.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SendMessageRequest request) {
        return messageService.send(me.id(), request);
    }
}
