package com.cipherchat.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-memory fixed-window counter. Good enough for a single instance; a multi-instance
 * deployment would move this to Redis (see DEPLOYMENT_AND_SUGGESTIONS.md).
 */
public class FixedWindowRateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    /** Records one hit. Returns the wait time if the key is over its limit, empty otherwise. */
    public Optional<Duration> hit(String key) {
        Instant now = clock.instant();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> !w.resetsAt.isAfter(now));
        }
        Window w = windows.compute(key, (k, existing) ->
                existing == null || !existing.resetsAt.isAfter(now)
                        ? new Window(now.plus(window), 1)
                        : new Window(existing.resetsAt, existing.count + 1));
        return w.count > limit ? Optional.of(Duration.between(now, w.resetsAt)) : Optional.empty();
    }

    /** Returns the wait time if the key is already over its limit, without recording a hit. */
    public Optional<Duration> check(String key) {
        Instant now = clock.instant();
        Window w = windows.get(key);
        if (w == null || !w.resetsAt.isAfter(now) || w.count < limit) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, w.resetsAt));
    }

    public void reset(String key) {
        windows.remove(key);
    }

    private record Window(Instant resetsAt, int count) {
    }
}
