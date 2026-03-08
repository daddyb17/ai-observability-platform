package com.aiobservability.services.incidentdetectionservice.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IncidentSignalRecord(
        UUID id,
        UUID incidentId,
        String signalType,
        String signalKey,
        Map<String, Object> signalPayload,
        Instant observedAt
) {
}
