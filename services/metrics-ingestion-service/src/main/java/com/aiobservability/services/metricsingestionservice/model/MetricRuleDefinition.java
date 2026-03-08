package com.aiobservability.services.metricsingestionservice.model;

public record MetricRuleDefinition(
        String ruleName,
        String metricName,
        double threshold,
        String window
) {
}
