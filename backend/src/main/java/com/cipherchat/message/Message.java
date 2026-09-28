package com.cipherchat.message;

import com.cipherchat.attachment.Attachment;
import com.cipherchat.conversation.Conversation;
import com.cipherchat.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    /** ASCII-armored OpenPGP message, signed and encrypted by the sender's browser. */
    @Column(nullable = false, length = 200000)
    private String ciphertext;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id")
    private Attachment attachment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Message() {
    }

    public Message(Conversation conversation, User sender, String ciphertext, Attachment attachment) {
        this.conversation = conversation;
        this.sender = sender;
        this.ciphertext = ciphertext;
        this.attachment = attachment;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public User getSender() {
        return sender;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public Attachment getAttachment() {
        return attachment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
