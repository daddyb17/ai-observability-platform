package com.aiobservability.services.aianalysisservice.model;

import java.time.Instant;

public record TraceSummaryRecord(
        String traceId,
        String rootService,
        Long durationMs,
        boolean errorFlag,
        Integer spanCount,
        String bottleneckService,
        String bottleneckSpan,
        Instant startedAt
) {
}
