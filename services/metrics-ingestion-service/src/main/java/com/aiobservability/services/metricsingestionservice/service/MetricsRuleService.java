package com.aiobservability.services.metricsingestionservice.service;

import com.aiobservability.services.metricsingestionservice.config.MetricsIngestionProperties;
import com.aiobservability.services.metricsingestionservice.model.MetricRuleDefinition;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MetricsRuleService {
    private final MetricsIngestionProperties properties;

    public MetricsRuleService(MetricsIngestionProperties properties) {
        this.properties = properties;
    }

    public List<MetricRuleDefinition> getRules() {
        MetricsIngestionProperties.RuleProperties rules = properties.rules();
        String window = properties.evaluationWindow();
        return List.of(
                new MetricRuleDefinition("error-rate", "http_server_requests_error_rate", rules.errorRateThreshold(), window),
                new MetricRuleDefinition("latency-p95", "http_server_requests_p95_latency_ms", rules.latencyP95ThresholdMs(), window),
                new MetricRuleDefinition("jvm-heap", "jvm_heap_usage_ratio", rules.jvmHeapThreshold(), window),
                new MetricRuleDefinition("db-connections", "db_pool_usage_ratio", rules.dbConnectionsThreshold(), window)
        );
    }
}
