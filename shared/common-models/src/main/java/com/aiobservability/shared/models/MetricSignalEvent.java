package com.aiobservability.shared.models;

import java.time.Instant;

public record MetricSignalEvent(
        String signalId,
        String serviceName,
        String metricName,
        double value,
        double threshold,
        String status,
        String window,
        Instant timestamp
) {
}
