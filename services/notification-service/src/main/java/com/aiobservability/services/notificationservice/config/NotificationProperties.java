package com.aiobservability.services.notificationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(
        List<String> channels,
        String webhookUrl,
        String slackWebhookUrl,
        boolean emailSinkEnabled,
        int maxRetries,
        long retryDelayMs,
        int queryLimitDefault,
        int queryLimitMax,
        KafkaProperties kafka
) {
    public NotificationProperties {
        channels = channels == null || channels.isEmpty() ? List.of("webhook", "mock-slack", "email-sink") : channels;
        maxRetries = maxRetries < 0 ? 2 : maxRetries;
        retryDelayMs = retryDelayMs < 0 ? 500 : retryDelayMs;
        queryLimitDefault = queryLimitDefault <= 0 ? 50 : queryLimitDefault;
        queryLimitMax = queryLimitMax <= 0 ? 200 : queryLimitMax;
        kafka = kafka == null ? new KafkaProperties(null, null, null, null, 0, null) : kafka;
    }

    public record KafkaProperties(
            String topicIncidentsDetected,
            String topicAiAnalysisResult,
            String topicAlertsOutbound,
            String deadLetterTopic,
            int sendTimeoutSeconds,
            String consumerGroupId
    ) {
        public KafkaProperties {
            topicIncidentsDetected = isBlank(topicIncidentsDetected) ? "incidents.detected" : topicIncidentsDetected;
            topicAiAnalysisResult = isBlank(topicAiAnalysisResult) ? "ai.analysis.result" : topicAiAnalysisResult;
            topicAlertsOutbound = isBlank(topicAlertsOutbound) ? "alerts.outbound" : topicAlertsOutbound;
            deadLetterTopic = isBlank(deadLetterTopic) ? "deadletter.alerts" : deadLetterTopic;
            sendTimeoutSeconds = sendTimeoutSeconds <= 0 ? 5 : sendTimeoutSeconds;
            consumerGroupId = isBlank(consumerGroupId) ? "notification-service" : consumerGroupId;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
