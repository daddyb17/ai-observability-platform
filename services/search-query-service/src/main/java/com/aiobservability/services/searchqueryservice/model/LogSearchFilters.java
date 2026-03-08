package com.aiobservability.services.searchqueryservice.model;

import java.time.Instant;

public record LogSearchFilters(
        String serviceName,
        String level,
        String traceId,
        String exceptionType,
        String text,
        Instant from,
        Instant to,
        int page,
        int size,
        String sort
) {
}
