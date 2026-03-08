package com.aiobservability.services.authservice.service;

import com.aiobservability.services.authservice.api.AuthResponse;
import com.aiobservability.services.authservice.api.UserProfileResponse;

public record AuthPayload(
        AuthResponse tokens,
        UserProfileResponse user
) {
}
