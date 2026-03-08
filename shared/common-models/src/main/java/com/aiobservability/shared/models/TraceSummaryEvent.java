package com.aiobservability.shared.models;

import java.time.Instant;

public record TraceSummaryEvent(
        String traceId,
        String rootService,
        long durationMs,
        boolean errorFlag,
        int spanCount,
        String bottleneckService,
        String bottleneckSpan,
        Instant startedAt
) {
}
