package com.aiobservability.services.aianalysisservice.model;

import java.util.List;
import java.util.Map;

public record AiAnalysisResult(
        String summary,
        String rootCause,
        double confidence,
        List<String> recommendedActions,
        List<String> evidence,
        String provider,
        String modelName,
        Map<String, Object> rawResponse,
        boolean fallbackUsed
) {
}
