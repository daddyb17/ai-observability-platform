package com.aiobservability.services.authservice.security;

import com.aiobservability.services.authservice.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";

    private final SecretKey secretKey;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public JwtService(
            @Value("${app.security.jwt-secret-base64}") String secretBase64,
            @Value("${app.security.access-token-ttl-seconds}") long accessTokenTtlSeconds,
            @Value("${app.security.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds
    ) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public String generateAccessToken(String username, Role role) {
        return generateToken(username, role, "access", accessTokenTtlSeconds);
    }

    public String generateRefreshToken(String username, Role role) {
        return generateToken(username, role, "refresh", refreshTokenTtlSeconds);
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return "access".equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return "refresh".equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration == null || expiration.toInstant().isBefore(Instant.now());
    }

    public Role extractRole(Claims claims) {
        return Role.valueOf(claims.get(CLAIM_ROLE, String.class));
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    private String generateToken(String username, Role role, String tokenType, long ttlSeconds) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(ttlSeconds);
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }
}
