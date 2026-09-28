package com.cipherchat.model;

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

/** A one-to-one conversation. {@code userA.id < userB.id} always holds. */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_a_id")
    private User userA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_b_id")
    private User userB;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    protected Conversation() {
    }

    private Conversation(User userA, User userB) {
        this.userA = userA;
        this.userB = userB;
        this.createdAt = Instant.now();
    }

    public static Conversation between(User first, User second) {
        if (first.getId().equals(second.getId())) {
            throw new IllegalArgumentException("A conversation needs two different users");
        }
        return first.getId() < second.getId() ? new Conversation(first, second) : new Conversation(second, first);
    }

    public boolean hasParticipant(Long userId) {
        return userA.getId().equals(userId) || userB.getId().equals(userId);
    }

    public User peerOf(Long userId) {
        return userA.getId().equals(userId) ? userB : userA;
    }

    public void touch(Instant when) {
        this.lastMessageAt = when;
    }

    public Long getId() {
        return id;
    }

    public User getUserA() {
        return userA;
    }

    public User getUserB() {
        return userB;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }
}
