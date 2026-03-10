package com.aiobservability.services.apigateway.security;

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
public class GatewayJwtService {
    private final SecretKey secretKey;

    public GatewayJwtService(@Value("${app.gateway.security.jwt-secret-base64}") String secretBase64) {
        this.secretKey = loadSecretKey(secretBase64);
    }

    public Claims parseAndValidateAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Date expiration = claims.getExpiration();
        if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Token expired");
        }
        String tokenType = claims.get("tokenType", String.class);
        if (!"access".equals(tokenType)) {
            throw new IllegalArgumentException("Not an access token");
        }
        return claims;
    }

    private SecretKey loadSecretKey(String secretBase64) {
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalStateException("Missing JWT secret. Set JWT_SECRET_BASE64.");
        }
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secretBase64);
            if (keyBytes.length < 32) {
                throw new IllegalStateException("JWT secret must decode to at least 32 bytes.");
            }
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT_SECRET_BASE64 must be valid Base64.", ex);
        }
    }
}
