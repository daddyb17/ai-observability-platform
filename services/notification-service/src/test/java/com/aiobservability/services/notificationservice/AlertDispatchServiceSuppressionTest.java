package com.aiobservability.services.notificationservice;

import com.aiobservability.services.notificationservice.channel.NotificationChannelClient;
import com.aiobservability.services.notificationservice.config.NotificationProperties;
import com.aiobservability.services.notificationservice.model.AlertMessage;
import com.aiobservability.services.notificationservice.repository.AlertNotificationRepository;
import com.aiobservability.services.notificationservice.service.AlertDispatchService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentMatchers;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertDispatchServiceSuppressionTest {
    @Test
    void suppressesDuplicateAlertsWithinWindow() {
        AlertNotificationRepository repository = mock(AlertNotificationRepository.class);
        NotificationChannelClient webhookClient = mock(NotificationChannelClient.class);
        when(webhookClient.channel()).thenReturn("webhook");
        when(repository.existsRecentByDedupKey(any(UUID.class), eq("webhook"), anyString(), any(Instant.class)))
                .thenReturn(true);

        NotificationProperties properties = new NotificationProperties(
                List.of("webhook"),
                "",
                "",
                true,
                true,
                10,
                2,
                100,
                50,
                200,
                new NotificationProperties.KafkaProperties(
                        "incidents.detected",
                        "ai.analysis.result",
                        "alerts.outbound",
                        "deadletter.alerts",
                        5,
                        "notification-service"
                )
        );
        AlertDispatchService service = new AlertDispatchService(properties, repository, List.of(webhookClient));

        AlertMessage message = new AlertMessage(
                UUID.randomUUID(),
                "HIGH",
                "Payment service timeout incident",
                "Timeout burst detected",
                List.of("Inspect DB pool"),
                "INCIDENT_DETECTED",
                Instant.parse("2026-03-08T10:16:10Z"),
                Map.of("incidentId", "INC-1001")
        );

        service.dispatch(message, List.of("webhook"));

        verify(repository).createSuppressed(
                eq(message.incidentId()),
                eq("webhook"),
                ArgumentMatchers.<Map<String, Object>>any(),
                anyString()
        );
        verify(repository, never()).createPending(any(UUID.class), anyString(), ArgumentMatchers.<Map<String, Object>>any());
        verify(webhookClient, never()).send(any(AlertMessage.class));
    }
}
