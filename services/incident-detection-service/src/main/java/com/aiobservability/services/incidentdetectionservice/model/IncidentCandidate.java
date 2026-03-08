package com.aiobservability.services.incidentdetectionservice.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record IncidentCandidate(
        String title,
        String description,
        String signalType,
        String signalKey,
        int weight,
        List<String> affectedServices,
        String rootTraceId,
        Map<String, Object> payload,
        Instant observedAt
) {
}
