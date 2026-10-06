package com.cipherchat.security;

import com.cipherchat.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <jwt>}. Invalid tokens are
 * ignored here; the security entry point then answers 401 for protected routes.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserService userService;

    public JwtAuthenticationFilter(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            jwtService.verify(header.substring(BEARER.length()).trim())
                    // Reject tokens for accounts that no longer exist.
                    .filter(user -> userService.exists(user.id()))
                    .ifPresent(user -> SecurityContextHolder.getContext().setAuthentication(toAuthentication(user)));
        }
        chain.doFilter(request, response);
    }

    public static UsernamePasswordAuthenticationToken toAuthentication(AuthUser user) {
        return UsernamePasswordAuthenticationToken.authenticated(user, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }
}
