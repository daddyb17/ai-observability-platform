package com.aiobservability.services.authservice.api;

import com.aiobservability.services.authservice.domain.Role;

import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String username,
        Role role
) {
}
