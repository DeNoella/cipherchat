package com.cipherchat.service;

import com.cipherchat.dto.AuthResponse;
import com.cipherchat.dto.LoginRequest;
import com.cipherchat.dto.RegisterRequest;
import com.cipherchat.exception.ApiException;
import com.cipherchat.model.User;
import com.cipherchat.security.AuthUser;
import com.cipherchat.security.JwtService;
import com.cipherchat.security.LoginAttemptGuard;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptGuard attemptGuard;
    private final PublicKeyService publicKeyService;
    private final KeyBackupService keyBackupService;
    /** Hash of a random value, compared against when the user does not exist to equalise timing. */
    private final String dummyHash;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginAttemptGuard attemptGuard, PublicKeyService publicKeyService,
                       KeyBackupService keyBackupService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.attemptGuard = attemptGuard;
        this.publicKeyService = publicKeyService;
        this.keyBackupService = keyBackupService;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-" + System.nanoTime());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp) {
        attemptGuard.beforeAttempt(clientIp, null);
        // One transaction: an invalid key or backup rolls the new account back.
        User user = userService.create(request.username(), request.password());
        if (request.publicKey() != null && !request.publicKey().isBlank()) {
            publicKeyService.applyTo(user, request.publicKey());
        }
        if (request.keyBackup() != null && !request.keyBackup().isBlank()) {
            keyBackupService.validate(user, request.keyBackup());
            // The Vault Transit context is the user's ID, which exists now that the account is saved.
            keyBackupService.store(user, request.keyBackup());
        }
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, String clientIp) {
        String username = UserService.normalizeUsername(request.username());
        attemptGuard.beforeAttempt(clientIp, username);
        User user = userService.findByUsername(username).orElse(null);
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
