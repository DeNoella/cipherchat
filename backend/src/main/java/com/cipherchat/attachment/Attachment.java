package com.cipherchat.attachment;

import com.cipherchat.conversation.Conversation;
import com.cipherchat.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for an encrypted attachment. The bytes live in {@link AttachmentStorage}.
 * File name and MIME type are deliberately absent: they travel inside the encrypted message.
 */
@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id")
    private User uploader;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Attachment() {
    }

    public Attachment(Conversation conversation, User uploader, long sizeBytes) {
        this.id = UUID.randomUUID();
        this.conversation = conversation;
        this.uploader = uploader;
        this.sizeBytes = sizeBytes;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public User getUploader() {
        return uploader;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
