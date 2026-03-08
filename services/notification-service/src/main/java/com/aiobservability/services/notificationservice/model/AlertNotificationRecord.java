package com.aiobservability.services.notificationservice.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AlertNotificationRecord(
        UUID id,
        UUID incidentId,
        String channel,
        Map<String, Object> payload,
        AlertStatus deliveryStatus,
        int attemptCount,
        Instant sentAt,
        String errorMessage
) {
}
