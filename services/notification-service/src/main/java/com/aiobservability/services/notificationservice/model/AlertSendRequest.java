package com.aiobservability.services.notificationservice.model;

import java.util.List;
import java.util.UUID;

public record AlertSendRequest(
        UUID incidentId,
        String severity,
        String title,
        String summary,
        List<String> recommendedActions,
        List<String> channels
) {
}
