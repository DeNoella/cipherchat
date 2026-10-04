package com.cipherchat.security;

import com.cipherchat.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;
    private static final String USER_ID_CLAIM = "uid";

    private final SecretKey key;
    private final AppProperties.Jwt config;

    public JwtService(AppProperties properties) {
        this.config = properties.jwt();
        byte[] secret = config.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String issue(AuthUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(config.issuer())
                .subject(user.username())
                .claim(USER_ID_CLAIM, user.id())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(config.ttl())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public long ttlSeconds() {
        return config.ttl().toSeconds();
    }

    /** Returns the principal if the token is well-formed, correctly signed, unexpired and ours. */
    public Optional<AuthUser> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(config.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = claims.get(USER_ID_CLAIM, Long.class);
            if (userId == null || claims.getSubject() == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthUser(userId, claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
