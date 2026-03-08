package com.aiobservability.services.authservice.api;

public record LogoutRequest(
        String refreshToken
) {
}
