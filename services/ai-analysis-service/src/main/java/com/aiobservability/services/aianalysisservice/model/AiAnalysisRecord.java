package com.aiobservability.services.aianalysisservice.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AiAnalysisRecord(
        UUID id,
        UUID incidentId,
        String provider,
        String modelName,
        String summary,
        String rootCause,
        double confidence,
        List<String> recommendedActions,
        List<String> evidence,
        Map<String, Object> rawResponse,
        Instant createdAt
) {
}
