package com.aiobservability.services.searchqueryservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(
        String elasticsearchUrl,
        String logIndexPattern,
        int defaultPageSize,
        int maxPageSize
) {
}
