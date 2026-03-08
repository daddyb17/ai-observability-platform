package com.aiobservability.services.traceingestionservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.trace-ingestion")
public record TraceIngestionProperties(
        String jaegerUrl,
        String elasticsearchUrl,
        String summaryIndex,
        String topic,
        String deadLetterTopic,
        int kafkaSendTimeoutSeconds,
        int searchLimitDefault,
        int searchLimitMax
) {
}
