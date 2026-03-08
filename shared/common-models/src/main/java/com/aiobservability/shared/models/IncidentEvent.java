package com.aiobservability.shared.models;

import java.time.Instant;
import java.util.List;

public record IncidentEvent(
        String incidentId,
        String title,
        Severity severity,
        IncidentStatus status,
        List<String> affectedServices,
        String dominantSignalType,
        Instant detectedAt
) {
}
