package com.aiobservability.services.aianalysisservice.model;

import java.time.Instant;
import java.util.Map;

public record IncidentSignalRecord(
        String signalType,
        String signalKey,
        Map<String, Object> signalPayload,
        Instant observedAt
) {
}
