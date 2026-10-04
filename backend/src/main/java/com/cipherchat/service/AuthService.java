package com.cipherchat.service;

import com.cipherchat.dto.AuthResponse;
import com.cipherchat.dto.LoginRequest;
import com.cipherchat.dto.RegisterRequest;
import com.cipherchat.exception.ApiException;
import com.cipherchat.model.User;
import com.cipherchat.repository.UserRepository;
import com.cipherchat.security.AuthUser;
import com.cipherchat.security.JwtService;
import com.cipherchat.security.LoginAttemptGuard;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptGuard attemptGuard;
    private final PublicKeyService publicKeyService;
    private final KeyBackupService keyBackupService;
    /** Hash of a random value, compared against when the user does not exist to equalise timing. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginAttemptGuard attemptGuard, PublicKeyService publicKeyService,
                       KeyBackupService keyBackupService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.attemptGuard = attemptGuard;
        this.publicKeyService = publicKeyService;
        this.keyBackupService = keyBackupService;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-" + System.nanoTime());
    }

    public static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp) {
        String username = normalizeUsername(request.username());
        attemptGuard.beforeAttempt(clientIp, null);
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("Password must be at most 72 bytes");
        }
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("Username is already taken");
        }
        User user = new User(username, passwordEncoder.encode(request.password()));
        if (request.publicKey() != null && !request.publicKey().isBlank()) {
            publicKeyService.applyTo(user, request.publicKey());
        }
        if (request.keyBackup() != null && !request.keyBackup().isBlank()) {
            keyBackupService.applyTo(user, request.keyBackup());
        }
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent registration of the same name.
            throw ApiException.conflict("Username is already taken");
        }
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, String clientIp) {
        String username = normalizeUsername(request.username());
        attemptGuard.beforeAttempt(clientIp, username);
        User user = users.findByUsername(username).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches) {
            attemptGuard.onFailure(username);
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        attemptGuard.onSuccess(username);
        return tokenFor(user);
    }

    private AuthResponse tokenFor(User user) {
        String token = jwtService.issue(new AuthUser(user.getId(), user.getUsername()));
        return new AuthResponse(token, "Bearer", jwtService.ttlSeconds(), user.getUsername(),
                user.hasPublicKey(), user.getKeyFingerprint(), user.hasKeyBackup());
    }
}
