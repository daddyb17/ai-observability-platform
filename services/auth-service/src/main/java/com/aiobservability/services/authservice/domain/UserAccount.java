package com.aiobservability.services.authservice.domain;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String username,
        String passwordHash,
        Role role,
        boolean enabled,
        Instant createdAt
) {
}
