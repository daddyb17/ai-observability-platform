package com.aiobservability.services.traceingestionservice.model;

public record TraceSummarizeRequest(
        String traceId,
        String incidentId
) {
}
