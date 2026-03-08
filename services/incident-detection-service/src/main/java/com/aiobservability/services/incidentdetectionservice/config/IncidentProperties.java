package com.aiobservability.services.incidentdetectionservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.incident")
public record IncidentProperties(
        RuleProperties rules,
        KafkaProperties kafka
) {
    public record RuleProperties(
            int correlationWindowMinutes,
            int errorBurstThreshold,
            int errorBurstWindowMinutes,
            int traceFailureThreshold,
            int traceFailureWindowMinutes
    ) {
    }

    public record KafkaProperties(
            String topicIncidentsDetected,
            String topicIncidentsUpdated,
            String topicAiAnalysisRequest,
            String topicAlertsOutbound,
            int sendTimeoutSeconds
    ) {
    }
}
