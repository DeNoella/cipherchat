package com.cipherchat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    public static final String USERNAME_REGEX = "^[A-Za-z0-9_]{3,32}$";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** ASCII-armored OpenPGP public key. Never a private key. */
    @Column(name = "public_key", length = 20000)
    private String publicKey;

    @Column(name = "key_fingerprint", length = 64)
    private String keyFingerprint;

    @Column(name = "key_algorithm", length = 64)
    private String keyAlgorithm;

    @Column(name = "key_created_at")
    private Instant keyCreatedAt;

    @Column(name = "key_uploaded_at")
    private Instant keyUploadedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public void setPublicKey(String armoredKey, String fingerprint, String algorithm, Instant keyCreatedAt) {
        this.publicKey = armoredKey;
        this.keyFingerprint = fingerprint;
        this.keyAlgorithm = algorithm;
        this.keyCreatedAt = keyCreatedAt;
        this.keyUploadedAt = Instant.now();
    }

    public boolean hasPublicKey() {
        return publicKey != null;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getKeyFingerprint() {
        return keyFingerprint;
    }

    public String getKeyAlgorithm() {
        return keyAlgorithm;
    }

    public Instant getKeyCreatedAt() {
        return keyCreatedAt;
    }

    public Instant getKeyUploadedAt() {
        return keyUploadedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
