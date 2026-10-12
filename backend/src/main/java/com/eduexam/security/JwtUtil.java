package com.eduexam.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-minutes}")
    private int expirationMinutes;

    @PostConstruct
    void validateConfiguration() {
        int length = secret != null ? secret.getBytes(StandardCharsets.UTF_8).length : 0;
        if (length < 32)
            throw new IllegalStateException("APP_JWT_SECRET must contain at least 32 UTF-8 bytes");
        if (java.util.Set.of(
                "CHANGE_ME",
                "replace-with-a-random-secret-of-at-least-32-characters",
                "eduexam-local-development-secret-change-me-32-chars"
        ).contains(secret))
            throw new IllegalStateException("APP_JWT_SECRET must be replaced with a private random secret");
        if (expirationMinutes <= 0)
            throw new IllegalStateException("app.jwt.expiration-minutes must be positive");
    }

    private SecretKey getKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long userId) {
        return generateToken(userId, 0);
    }

    public String generateToken(Long userId, Integer tokenVersion) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("ver", tokenVersion != null ? tokenVersion : 0)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + (long) expirationMinutes * 60 * 1000))
                .signWith(getKey())
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.getSubject() != null ? Long.parseLong(claims.getSubject()) : null;
    }

    public int getTokenVersionFromToken(String token) {
        Number version = getClaims(token).get("ver", Number.class);
        return version != null ? version.intValue() : 0;
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
