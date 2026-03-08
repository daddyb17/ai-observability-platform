package com.aiobservability.shared.security;

import java.util.Set;

public record JwtUserContext(
        String userId,
        String username,
        Set<String> roles
) {
}
