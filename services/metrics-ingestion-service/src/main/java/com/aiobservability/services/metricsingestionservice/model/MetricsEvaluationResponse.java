package com.aiobservability.services.metricsingestionservice.model;

import com.aiobservability.shared.models.MetricSignalEvent;

import java.time.Instant;
import java.util.List;

public record MetricsEvaluationResponse(
        Instant evaluatedAt,
        int evaluatedServices,
        int breachedSignals,
        List<MetricSignalEvent> signals
) {
}
