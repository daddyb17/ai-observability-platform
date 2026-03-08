package com.aiobservability.services.notificationservice.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AlertMessage(
        UUID incidentId,
        String severity,
        String title,
        String summary,
        List<String> recommendedActions,
        String sourceEventType,
        Instant occurredAt,
        Map<String, Object> rawPayload
) {
}
