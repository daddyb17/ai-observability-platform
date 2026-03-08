package com.aiobservability.services.logingestionservice.service;

import com.aiobservability.services.logingestionservice.model.EnrichedLogEvent;
import com.aiobservability.services.logingestionservice.model.LogIngestionRequest;
import com.aiobservability.services.logingestionservice.validation.LogValidationService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class LogEnrichmentService {
    private final LogValidationService logValidationService;

    public LogEnrichmentService(LogValidationService logValidationService) {
        this.logValidationService = logValidationService;
    }

    public EnrichedLogEvent enrich(LogIngestionRequest request) {
        String normalizedLevel = logValidationService.normalizeLevel(request.level());
        Map<String, String> tags = request.tags() == null ? new HashMap<>() : new HashMap<>(request.tags());
        String host = request.host() == null || request.host().isBlank() ? "unknown-host" : request.host();
        return new EnrichedLogEvent(
                UUID.randomUUID().toString(),
                request.serviceName(),
                request.environment(),
                request.timestamp(),
                normalizedLevel,
                request.message(),
                request.exceptionType(),
                request.stackTrace(),
                request.traceId(),
                request.spanId(),
                host,
                tags,
                Instant.now()
        );
    }
}
