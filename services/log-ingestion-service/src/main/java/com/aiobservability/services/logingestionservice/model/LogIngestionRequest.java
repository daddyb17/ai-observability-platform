package com.aiobservability.services.logingestionservice.model;

import java.time.Instant;
import java.util.Map;

public record LogIngestionRequest(
        String serviceName,
        String environment,
        Instant timestamp,
        String level,
        String message,
        String exceptionType,
        String stackTrace,
        String traceId,
        String spanId,
        String host,
        Map<String, String> tags
) {
}
