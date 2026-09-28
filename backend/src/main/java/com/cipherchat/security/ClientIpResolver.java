package com.cipherchat.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Uses the socket address only. Behind a trusted reverse proxy, enable
 * {@code server.forward-headers-strategy=native} so Tomcat rewrites it from X-Forwarded-For;
 * reading that header directly would let clients spoof their IP and dodge rate limits.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
