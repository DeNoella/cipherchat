package com.cipherchat.service;

import com.cipherchat.config.AppProperties;
import com.cipherchat.dto.AttachmentResponse;
import com.cipherchat.exception.ApiException;
import com.cipherchat.model.Attachment;
import com.cipherchat.model.Conversation;
import com.cipherchat.model.User;
import com.cipherchat.repository.AttachmentRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService {

    public record Download(Resource content, long sizeBytes) {
    }

    private final AttachmentRepository attachments;
    private final AttachmentStorage storage;
    private final ConversationService conversationService;
    private final OpenPgpInspector inspector;
    private final PublicKeyService publicKeyService;
    private final long maxEncryptedBytes;

    public AttachmentService(AttachmentRepository attachments, AttachmentStorage storage,
                             ConversationService conversationService, OpenPgpInspector inspector,
                             PublicKeyService publicKeyService, AppProperties properties) {
        this.attachments = attachments;
        this.storage = storage;
        this.conversationService = conversationService;
        this.inspector = inspector;
        this.publicKeyService = publicKeyService;
        this.maxEncryptedBytes = properties.attachments().maxEncryptedBytes();
    }

    /** Accepts an OpenPGP-encrypted blob addressed to both participants. File name and type stay client-side. */
    @Transactional
    public AttachmentResponse upload(Long userId, String recipientUsername, MultipartFile file) {
        if (file.isEmpty()) {
            throw ApiException.badRequest("Attachment is empty");
        }
        if (file.getSize() > maxEncryptedBytes) {
            throw ApiException.payloadTooLarge("Attachment exceeds the 10 MB limit");
        }
        Conversation conversation = conversationService.getOrCreate(userId, recipientUsername);
        User recipient = conversation.peerOf(userId);
        User sender = conversation.peerOf(recipient.getId());

        Set<Long> keyIds;
        try (InputStream in = file.getInputStream()) {
            keyIds = inspector.encryptedMessageRecipients(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        publicKeyService.requireAddressedToBoth(keyIds, sender, recipient);

        Attachment attachment = attachments.save(new Attachment(conversation, sender, file.getSize()));
        try (InputStream in = file.getInputStream()) {
            storage.store(attachment.getId(), in);
        } catch (IOException e) {
            storage.delete(attachment.getId());
            throw new UncheckedIOException(e);
        }
        return AttachmentResponse.from(attachment);
    }

    /** Participants only; everyone else gets 404 so ids cannot be probed. */
    @Transactional(readOnly = true)
    public Download download(Long userId, UUID id) {
        Attachment attachment = attachments.findById(id)
                .filter(a -> a.getConversation().hasParticipant(userId))
                .orElseThrow(() -> ApiException.notFound("Attachment not found"));
        Resource content = storage.load(id).orElseThrow(() -> ApiException.notFound("Attachment not found"));
        return new Download(content, attachment.getSizeBytes());
    }
}
