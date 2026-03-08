package com.aiobservability.services.traceingestionservice.model;

import java.time.Instant;

public record TraceSummaryDocument(
        String traceId,
        String incidentId,
        String rootService,
        long durationMs,
        boolean errorFlag,
        int spanCount,
        String bottleneckService,
        String bottleneckSpan,
        Instant startedAt
) {
}
