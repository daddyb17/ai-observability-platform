package com.aiobservability.services.aianalysisservice.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentContext(
        UUID id,
        String code,
        String title,
        String description,
        String severity,
        String status,
        List<String> affectedServices,
        String rootTraceId,
        Instant createdAt,
        Instant updatedAt
) {
}
