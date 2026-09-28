package com.cipherchat.service;

import com.cipherchat.config.AppProperties;
import com.cipherchat.dto.MessageResponse;
import com.cipherchat.dto.SendMessageRequest;
import com.cipherchat.exception.ApiException;
import com.cipherchat.model.Attachment;
import com.cipherchat.model.Conversation;
import com.cipherchat.model.Message;
import com.cipherchat.model.User;
import com.cipherchat.repository.AttachmentRepository;
import com.cipherchat.repository.MessageRepository;
import com.cipherchat.security.AuthUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class MessageService {

    public static final int MAX_PAGE_SIZE = 100;

    /** Published after a message is committed; the WebSocket layer relays it to both participants. */
    public record MessageSentEvent(MessageResponse message, String sender, String recipient) {
    }

    private final MessageRepository messages;
    private final AttachmentRepository attachments;
    private final ConversationService conversationService;
    private final OpenPgpInspector inspector;
    private final ApplicationEventPublisher events;
    private final int maxCiphertextChars;

    public MessageService(MessageRepository messages, AttachmentRepository attachments,
                          ConversationService conversationService, OpenPgpInspector inspector,
                          ApplicationEventPublisher events, AppProperties properties) {
        this.messages = messages;
        this.attachments = attachments;
        this.conversationService = conversationService;
        this.inspector = inspector;
        this.events = events;
        this.maxCiphertextChars = properties.messages().maxCiphertextChars();
    }

    @Transactional
    public MessageResponse send(AuthUser me, SendMessageRequest request) {
        if (request.ciphertext().length() > maxCiphertextChars) {
            throw ApiException.payloadTooLarge("Message is too large");
        }
        Conversation conversation = conversationService.getOrCreate(me.id(), request.recipientUsername());
        User recipient = conversation.peerOf(me.id());
        User sender = conversation.peerOf(recipient.getId());

        if (!sender.hasPublicKey()) {
            throw ApiException.badRequest("Upload your public key before sending messages");
        }
        if (!recipient.hasPublicKey()) {
            throw ApiException.badRequest("Recipient has not uploaded a public key yet");
        }
        requireAddressedToBoth(inspector.encryptedMessageRecipients(request.ciphertext()), sender, recipient);

        Attachment attachment = null;
        if (request.attachmentId() != null) {
            attachment = attachments.findById(request.attachmentId())
                    .filter(a -> a.getConversation().getId().equals(conversation.getId())
                            && a.getUploader().getId().equals(me.id()))
                    .orElseThrow(() -> ApiException.badRequest("Unknown attachment"));
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

    /**
     * Enforces the E2EE contract server-side: the ciphertext must be addressed to the recipient's
     * current key (so they can read it) and to the sender's key (so the sender can read their history).
     */
    private void requireAddressedToBoth(Set<Long> recipients, User sender, User recipient) {
        Set<Long> recipientKeys = inspector.inspectPublicKey(recipient.getPublicKey()).encryptionKeyIds();
        Set<Long> senderKeys = inspector.inspectPublicKey(sender.getPublicKey()).encryptionKeyIds();
        if (Collections.disjoint(recipients, recipientKeys)) {
            throw ApiException.badRequest("Message is not encrypted to the recipient's current public key");
        }
        if (Collections.disjoint(recipients, senderKeys)) {
            throw ApiException.badRequest("Message must also be encrypted to your own public key");
        }
    }
}
