package com.cipherchat.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    @Test
    void blocksAfterLimitAndRecoversAfterWindow() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, Duration.ofMinutes(1), clock);

        assertThat(limiter.hit("k")).isEmpty();
        assertThat(limiter.hit("k")).isEmpty();
        assertThat(limiter.hit("k")).isEmpty();
        assertThat(limiter.check("k")).contains(Duration.ofMinutes(1));
        assertThat(limiter.hit("k")).isPresent();

        now.set(now.get().plusSeconds(61));
        assertThat(limiter.check("k")).isEmpty();
        assertThat(limiter.hit("k")).isEmpty();
    }

    @Test
    void keysAreIndependentAndResettable() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, Duration.ofMinutes(1), clock);

        limiter.hit("a");
        assertThat(limiter.check("a")).isPresent();
        assertThat(limiter.check("b")).isEmpty();

        limiter.reset("a");
        assertThat(limiter.check("a")).isEmpty();
    }
}
