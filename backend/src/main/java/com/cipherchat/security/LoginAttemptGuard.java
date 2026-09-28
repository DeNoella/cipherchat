package com.cipherchat.security;

import com.cipherchat.common.RateLimitExceededException;
import com.cipherchat.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Brute-force protection for authentication endpoints:
 * <ul>
 *   <li>per username: at most {@code maxAttempts} <em>failed</em> logins per window (reset on success);</li>
 *   <li>per client IP: at most {@code maxAttemptsPerIp} auth requests per window (limits password spraying).</li>
 * </ul>
 */
@Component
public class LoginAttemptGuard {

    private final FixedWindowRateLimiter perUsernameFailures;
    private final FixedWindowRateLimiter perIp;

    public LoginAttemptGuard(AppProperties properties, Clock clock) {
        AppProperties.LoginRateLimit cfg = properties.loginRateLimit();
        this.perUsernameFailures = new FixedWindowRateLimiter(cfg.maxAttempts(), cfg.window(), clock);
        this.perIp = new FixedWindowRateLimiter(cfg.maxAttemptsPerIp(), cfg.window(), clock);
    }

    /** Call before processing any auth request. */
    public void beforeAttempt(String clientIp, String username) {
        perIp.hit("ip:" + clientIp).ifPresent(wait -> {
            throw new RateLimitExceededException(wait);
        });
        if (username != null) {
            perUsernameFailures.check("user:" + username).ifPresent(wait -> {
                throw new RateLimitExceededException(wait);
            });
        }
    }

    public void onFailure(String username) {
        perUsernameFailures.hit("user:" + username);
    }

    public void onSuccess(String username) {
        perUsernameFailures.reset("user:" + username);
    }
}
