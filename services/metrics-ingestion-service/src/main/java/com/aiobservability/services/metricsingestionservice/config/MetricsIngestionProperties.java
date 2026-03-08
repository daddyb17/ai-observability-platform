package com.aiobservability.services.metricsingestionservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.metrics-ingestion")
public record MetricsIngestionProperties(
        String prometheusUrl,
        String topic,
        String deadLetterTopic,
        int kafkaSendTimeoutSeconds,
        String evaluationWindow,
        long evaluateFixedDelayMs,
        int defaultIncidentSignalsSize,
        List<MonitoredService> services,
        RuleProperties rules
) {
    public record MonitoredService(
            String serviceName,
            String instanceRegex
    ) {
    }

    public record RuleProperties(
            double errorRateThreshold,
            double latencyP95ThresholdMs,
            double jvmHeapThreshold,
            double dbConnectionsThreshold
    ) {
    }
}
