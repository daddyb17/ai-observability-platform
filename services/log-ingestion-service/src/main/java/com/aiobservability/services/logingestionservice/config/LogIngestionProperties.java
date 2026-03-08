package com.aiobservability.services.logingestionservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.log-ingestion")
public record LogIngestionProperties(
        String topic,
        String deadLetterTopic,
        int kafkaMaxAttempts,
        int kafkaSendTimeoutSeconds,
        String elasticsearchUrl,
        String indexPrefix,
        int elasticsearchMaxAttempts,
        long elasticsearchBackoffMs
) {
}
