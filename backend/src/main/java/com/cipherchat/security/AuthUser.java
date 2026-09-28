package com.cipherchat.security;

import java.security.Principal;

/**
 * The authenticated principal, taken from a verified JWT. Implements {@link Principal} so that
 * STOMP user destinations ({@code /user/queue/...}) resolve by username.
 */
public record AuthUser(Long id, String username) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
