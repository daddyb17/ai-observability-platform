package com.aiobservability.services.metricsingestionservice.model;

import java.time.Instant;
import java.util.Map;

public record ServiceMetricSnapshot(
        String serviceName,
        Instant evaluatedAt,
        Map<String, Double> metrics
) {
}
