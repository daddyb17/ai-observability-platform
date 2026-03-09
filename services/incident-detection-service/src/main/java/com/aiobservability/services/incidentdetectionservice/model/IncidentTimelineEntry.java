package com.aiobservability.services.incidentdetectionservice.model;

import java.time.Instant;
import java.util.Map;

public record IncidentTimelineEntry(
        Instant timestamp,
        String eventType,
        String message,
        Map<String, Object> details
) {
}
