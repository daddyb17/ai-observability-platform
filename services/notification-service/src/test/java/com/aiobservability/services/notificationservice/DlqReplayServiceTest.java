package com.aiobservability.services.notificationservice;

import com.aiobservability.services.notificationservice.config.NotificationProperties;
import com.aiobservability.services.notificationservice.model.DlqReplayRequest;
import com.aiobservability.services.notificationservice.service.DlqReplayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DlqReplayServiceTest {
    @SuppressWarnings("unchecked")
    @Test
    void replaysFromDirectTopicAndPayloadFields() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn((CompletableFuture) CompletableFuture.completedFuture(null));

        DlqReplayService service = new DlqReplayService(kafkaTemplate, properties(), new ObjectMapper());
        Map<String, Object> response = service.replay(new DlqReplayRequest(
                "logs.raw",
                "{\"hello\":\"world\"}",
                "incident-1",
                null
        ));

        assertEquals("replayed", response.get("status"));
        assertEquals("logs.raw", response.get("originalTopic"));
        assertEquals("incident-1", response.get("key"));
        verify(kafkaTemplate).send("logs.raw", "incident-1", "{\"hello\":\"world\"}");
    }

    @SuppressWarnings("unchecked")
    @Test
    void replaysFromDlqEnvelopeMessage() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn((CompletableFuture) CompletableFuture.completedFuture(null));

        DlqReplayService service = new DlqReplayService(kafkaTemplate, properties(), new ObjectMapper());
        String dlqMessage = """
                {
                  "originalTopic": "ai.analysis.result",
                  "originalPayload": {
                    "eventType": "AI_ANALYSIS_RESULT",
                    "payload": {"incidentId":"INC-1001"}
                  }
                }
                """;

        Map<String, Object> response = service.replay(new DlqReplayRequest(
                null,
                null,
                null,
                dlqMessage
        ));

        assertEquals("replayed", response.get("status"));
        assertEquals("ai.analysis.result", response.get("originalTopic"));
        assertEquals("dlq-replay", response.get("key"));
        verify(kafkaTemplate).send(
                "ai.analysis.result",
                "dlq-replay",
                "{\"eventType\":\"AI_ANALYSIS_RESULT\",\"payload\":{\"incidentId\":\"INC-1001\"}}"
        );
    }

    @Test
    void rejectsMissingTopicAndPayload() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DlqReplayService service = new DlqReplayService(kafkaTemplate, properties(), new ObjectMapper());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.replay(new DlqReplayRequest(null, null, null, null))
        );
    }

    private NotificationProperties properties() {
        return new NotificationProperties(
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
    }
}
