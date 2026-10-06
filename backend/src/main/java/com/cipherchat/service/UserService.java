package com.cipherchat.service;

import com.cipherchat.dto.ChangePasswordRequest;
import com.cipherchat.dto.UserProfileResponse;
import com.cipherchat.dto.UserSummary;
import com.cipherchat.exception.ApiException;
import com.cipherchat.model.User;
import com.cipherchat.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Everything about user accounts: create, look up, profile, password and search. */
@Service
public class UserService {

    private static final int MAX_RESULTS = 20;
    /** BCrypt only uses the first 72 bytes of a password. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    /** Creates an account with a BCrypt-hashed password. Keys are added later via the key services. */
    @Transactional
    public User create(String username, String rawPassword) {
        String normalized = normalizeUsername(username);
        requirePasswordFitsBcrypt(rawPassword);
        if (users.existsByUsername(normalized)) {
            throw ApiException.conflict("Username is already taken");
        }
        try {
            return users.saveAndFlush(new User(normalized, passwordEncoder.encode(rawPassword)));
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent registration of the same name.
            throw ApiException.conflict("Username is already taken");
        }
    }

    /** The signed-in user. A valid token for a deleted account is treated as signed out. */
    @Transactional(readOnly = true)
    public User getById(Long userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return findByUsername(username).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return users.findByUsername(normalizeUsername(username));
    }

    @Transactional(readOnly = true)
    public boolean exists(Long userId) {
        return users.existsById(userId);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(Long userId) {
        return UserProfileResponse.from(getById(userId));
    }

    /** What anyone signed in may see about another user. */
    @Transactional(readOnly = true)
    public UserSummary summary(String username) {
        return UserSummary.from(getByUsername(username));
    }

    /** Prefix search excluding the caller. The query is pre-validated to [A-Za-z0-9_]. */
    @Transactional(readOnly = true)
    public List<UserSummary> search(String query, String excludeUsername) {
        String prefix = normalizeUsername(query).replace("_", "\\_");
        return users.searchByPrefix(prefix, excludeUsername, PageRequest.of(0, MAX_RESULTS)).stream()
                .map(UserSummary::from)
                .toList();
    }

    /**
     * Changes the login password after checking the current one. The key passphrase, the private
     * key backup and existing tokens are not affected.
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getById(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        requirePasswordFitsBcrypt(request.newPassword());
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("New password must be different from the current one");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private static void requirePasswordFitsBcrypt(String rawPassword) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw ApiException.badRequest("Password must be at most 72 bytes");
        }
    }
}
