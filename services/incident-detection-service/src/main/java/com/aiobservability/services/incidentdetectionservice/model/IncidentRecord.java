package com.aiobservability.services.incidentdetectionservice.model;

import com.aiobservability.shared.models.IncidentStatus;
import com.aiobservability.shared.models.Severity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentRecord(
        UUID id,
        String code,
        String title,
        String description,
        Severity severity,
        IncidentStatus status,
        List<String> affectedServices,
        String dominantSignalType,
        String dominantSignalKey,
        String rootTraceId,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt
) {
}
