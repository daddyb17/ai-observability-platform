package com.aiobservability.services.authservice.service;

import com.aiobservability.services.authservice.api.AuthResponse;
import com.aiobservability.services.authservice.api.UserProfileResponse;
import com.aiobservability.services.authservice.domain.UserAccount;
import com.aiobservability.services.authservice.security.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {
    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Map<String, Instant> refreshTokens = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userRefreshTokenIndex = new ConcurrentHashMap<>();

    public AuthService(
            UserAccountService userAccountService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthPayload login(String username, String password) {
        UserAccount user = userAccountService.findByUsername(username);
        if (user == null || !user.enabled() || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials");
        }

        String accessToken = jwtService.generateAccessToken(user.username(), user.role());
        String refreshToken = jwtService.generateRefreshToken(user.username(), user.role());
        registerRefreshToken(user.username(), refreshToken);

        AuthResponse tokens = new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenTtlSeconds()
        );
        UserProfileResponse profile = new UserProfileResponse(user.id(), user.username(), user.role());
        return new AuthPayload(tokens, profile);
    }

    public AuthPayload refresh(String refreshToken) {
        Claims claims = parseOrUnauthorized(refreshToken);
        if (!jwtService.isRefreshToken(claims) || jwtService.isExpired(claims)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token");
        }
        if (!refreshTokens.containsKey(refreshToken)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Refresh token not active");
        }

        String username = claims.getSubject();
        UserAccount user = userAccountService.findByUsername(username);
        if (user == null || !user.enabled()) {
            throw new ResponseStatusException(FORBIDDEN, "User disabled");
        }

        invalidateRefreshToken(refreshToken);
        String newAccessToken = jwtService.generateAccessToken(user.username(), user.role());
        String newRefreshToken = jwtService.generateRefreshToken(user.username(), user.role());
        registerRefreshToken(user.username(), newRefreshToken);

        AuthResponse tokens = new AuthResponse(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                jwtService.getAccessTokenTtlSeconds()
        );
        UserProfileResponse profile = new UserProfileResponse(user.id(), user.username(), user.role());
        return new AuthPayload(tokens, profile);
    }

    public void logout(String refreshToken, String usernameFromAccessToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            invalidateRefreshToken(refreshToken);
            return;
        }
        if (usernameFromAccessToken != null && !usernameFromAccessToken.isBlank()) {
            Set<String> tokens = userRefreshTokenIndex.remove(usernameFromAccessToken);
            if (tokens != null) {
                tokens.forEach(refreshTokens::remove);
            }
        }
    }

    public UserProfileResponse me(String username) {
        UserAccount user = userAccountService.findByUsername(username);
        if (user == null || !user.enabled()) {
            throw new ResponseStatusException(UNAUTHORIZED, "User not found");
        }
        return new UserProfileResponse(user.id(), user.username(), user.role());
    }

    public Claims parseAccessToken(String token) {
        Claims claims = parseOrUnauthorized(token);
        if (!jwtService.isAccessToken(claims) || jwtService.isExpired(claims)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid access token");
        }
        return claims;
    }

    private Claims parseOrUnauthorized(String token) {
        try {
            return jwtService.parseClaims(token);
        } catch (Exception ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "Token validation failed");
        }
    }

    private void registerRefreshToken(String username, String refreshToken) {
        refreshTokens.put(refreshToken, Instant.now());
        userRefreshTokenIndex.computeIfAbsent(username, ignored -> ConcurrentHashMap.newKeySet()).add(refreshToken);
    }

    private void invalidateRefreshToken(String refreshToken) {
        refreshTokens.remove(refreshToken);
        userRefreshTokenIndex.values().forEach(tokens -> tokens.remove(refreshToken));
    }
}
