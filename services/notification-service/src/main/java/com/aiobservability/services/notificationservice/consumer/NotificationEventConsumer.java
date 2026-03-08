package com.aiobservability.services.notificationservice.consumer;

import com.aiobservability.services.notificationservice.kafka.AlertDeadLetterPublisher;
import com.aiobservability.services.notificationservice.service.NotificationEventService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {
    private final NotificationEventService notificationEventService;
    private final AlertDeadLetterPublisher deadLetterPublisher;

    public NotificationEventConsumer(
            NotificationEventService notificationEventService,
            AlertDeadLetterPublisher deadLetterPublisher
    ) {
        this.notificationEventService = notificationEventService;
        this.deadLetterPublisher = deadLetterPublisher;
    }

    @KafkaListener(
            topics = "${app.notifications.kafka.topic-incidents-detected:incidents.detected}",
            groupId = "${app.notifications.kafka.consumer-group-id:notification-service}"
    )
    public void onIncidentDetected(String message) {
        consume("incidents.detected", message);
    }

    @KafkaListener(
            topics = "${app.notifications.kafka.topic-ai-analysis-result:ai.analysis.result}",
            groupId = "${app.notifications.kafka.consumer-group-id:notification-service}"
    )
    public void onAiAnalysisResult(String message) {
        consume("ai.analysis.result", message);
    }

    @KafkaListener(
            topics = "${app.notifications.kafka.topic-alerts-outbound:alerts.outbound}",
            groupId = "${app.notifications.kafka.consumer-group-id:notification-service}"
    )
    public void onAlertsOutbound(String message) {
        consume("alerts.outbound", message);
    }

    private void consume(String topic, String message) {
        try {
            notificationEventService.handleEnvelopeMessage(topic, message);
        } catch (Exception ex) {
            deadLetterPublisher.publish(topic, message, ex);
        }
    }
}
