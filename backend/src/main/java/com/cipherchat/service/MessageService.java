package com.cipherchat.service;

import com.cipherchat.config.AppProperties;
import com.cipherchat.dto.MessageResponse;
import com.cipherchat.dto.SendMessageRequest;
import com.cipherchat.exception.ApiException;
import com.cipherchat.model.Attachment;
import com.cipherchat.model.Conversation;
import com.cipherchat.model.Message;
import com.cipherchat.model.User;
import com.cipherchat.repository.MessageRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class MessageService {

    public static final int MAX_PAGE_SIZE = 100;

    /** Published after a message is committed; the WebSocket layer relays it to both participants. */
    public record MessageSentEvent(MessageResponse message, String sender, String recipient) {
    }

    private final MessageRepository messages;
    private final AttachmentService attachmentService;
    private final ConversationService conversationService;
    private final OpenPgpInspector inspector;
    private final PublicKeyService publicKeyService;
    private final ApplicationEventPublisher events;
    private final int maxCiphertextChars;

    public MessageService(MessageRepository messages, AttachmentService attachmentService,
                          ConversationService conversationService, OpenPgpInspector inspector,
                          PublicKeyService publicKeyService,
                          ApplicationEventPublisher events, AppProperties properties) {
        this.messages = messages;
        this.attachmentService = attachmentService;
        this.conversationService = conversationService;
        this.inspector = inspector;
        this.publicKeyService = publicKeyService;
        this.events = events;
        this.maxCiphertextChars = properties.messages().maxCiphertextChars();
    }

    @Transactional
    public MessageResponse send(Long userId, SendMessageRequest request) {
        if (request.ciphertext().length() > maxCiphertextChars) {
            throw ApiException.payloadTooLarge("Message is too large");
        }
        Conversation conversation = conversationService.getOrCreate(userId, request.recipientUsername());
        User recipient = conversation.peerOf(userId);
        User sender = conversation.peerOf(recipient.getId());

        publicKeyService.requireAddressedToBoth(inspector.encryptedMessageRecipients(request.ciphertext()),
                sender, recipient);

        Attachment attachment = null;
        if (request.attachmentId() != null) {
            attachment = attachmentService.requireUploadedBy(request.attachmentId(), conversation.getId(), userId);
            if (messages.existsByAttachmentId(attachment.getId())) {
                throw ApiException.badRequest("Attachment is already linked to a message");
            }
        }

        Message saved = messages.save(new Message(conversation, sender, request.ciphertext(), attachment));
        conversation.touch(saved.getCreatedAt());
        MessageResponse response = MessageResponse.from(saved);
        events.publishEvent(new MessageSentEvent(response, sender.getUsername(), recipient.getUsername()));
        return response;
    }

    /** Messages oldest-first, paging backwards from {@code beforeId}. */
    @Transactional(readOnly = true)
    public List<MessageResponse> history(Long conversationId, Long userId, Long beforeId, int size) {
        conversationService.requireParticipant(conversationId, userId);
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        List<MessageResponse> page = new ArrayList<>(messages.findPage(conversationId, beforeId, PageRequest.of(0, pageSize))
                .stream().map(MessageResponse::from).toList());
        Collections.reverse(page);
        return page;
    }
}
