package com.cipherchat.security;

/** The authenticated principal, taken from a verified JWT. */
public record AuthUser(Long id, String username) {
}
