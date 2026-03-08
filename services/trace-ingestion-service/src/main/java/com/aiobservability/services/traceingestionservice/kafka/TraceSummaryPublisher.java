package com.aiobservability.services.traceingestionservice.kafka;

import com.aiobservability.services.traceingestionservice.config.TraceIngestionProperties;
import com.aiobservability.services.traceingestionservice.model.TraceSummaryDocument;
import com.aiobservability.shared.models.EventEnvelope;
import com.aiobservability.shared.models.TraceSummaryEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class TraceSummaryPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TraceIngestionProperties properties;

    public TraceSummaryPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            TraceIngestionProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void publish(TraceSummaryDocument summary) {
        try {
            TraceSummaryEvent payload = new TraceSummaryEvent(
                    summary.traceId(),
                    summary.rootService(),
                    summary.durationMs(),
                    summary.errorFlag(),
                    summary.spanCount(),
                    summary.bottleneckService(),
                    summary.bottleneckSpan(),
                    summary.startedAt()
            );
            EventEnvelope<TraceSummaryEvent> envelope = new EventEnvelope<>(
                    summary.traceId(),
                    "TRACE_SUMMARY_GENERATED",
                    "1.0",
                    Instant.now(),
                    "trace-ingestion-service",
                    payload
            );
            String message = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(properties.topic(), summary.traceId(), message)
                    .get(properties.kafkaSendTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception ex) {
            publishDeadLetter(summary, ex);
            throw new IllegalStateException("Failed to publish trace summary", ex);
        }
    }

    private void publishDeadLetter(TraceSummaryDocument summary, Exception error) {
        try {
            Map<String, Object> dlqPayload = new LinkedHashMap<>();
            dlqPayload.put("originalTopic", properties.topic());
            dlqPayload.put("partition", null);
            dlqPayload.put("offset", null);
            dlqPayload.put("errorType", error.getClass().getSimpleName());
            dlqPayload.put("errorMessage", error.getMessage());
            dlqPayload.put("originalPayload", summary);
            dlqPayload.put("failedAt", Instant.now());
            String payload = objectMapper.writeValueAsString(dlqPayload);
            kafkaTemplate.send(properties.deadLetterTopic(), summary.traceId(), payload)
                    .get(properties.kafkaSendTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }
}
