package com.aiobservability.services.logingestionservice.kafka;

import com.aiobservability.services.logingestionservice.config.LogIngestionProperties;
import com.aiobservability.services.logingestionservice.model.EnrichedLogEvent;
import com.aiobservability.shared.models.EventEnvelope;
import com.aiobservability.shared.models.LogEventPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class LogKafkaPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final LogIngestionProperties properties;

    public LogKafkaPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            LogIngestionProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void publish(EnrichedLogEvent logEvent) {
        EventEnvelope<LogEventPayload> envelope = new EventEnvelope<>(
                logEvent.eventId(),
                "LOG_RECEIVED",
                "1.0",
                Instant.now(),
                "log-ingestion-service",
                toPayload(logEvent)
        );
        String payloadJson = toJson(envelope);
        String key = logEvent.traceId() == null || logEvent.traceId().isBlank()
                ? logEvent.serviceName()
                : logEvent.traceId();

        Exception lastFailure = null;
        for (int attempt = 1; attempt <= properties.kafkaMaxAttempts(); attempt++) {
            try {
                kafkaTemplate.send(properties.topic(), key, payloadJson)
                        .get(properties.kafkaSendTimeoutSeconds(), TimeUnit.SECONDS);
                return;
            } catch (Exception ex) {
                lastFailure = ex;
            }
        }

        publishToDeadLetter(logEvent, payloadJson, lastFailure);
        throw new IllegalStateException("Failed to publish log event to Kafka topic " + properties.topic(), lastFailure);
    }

    private void publishToDeadLetter(EnrichedLogEvent logEvent, String originalPayload, Exception error) {
        try {
            Map<String, Object> dlqPayload = new LinkedHashMap<>();
            dlqPayload.put("originalTopic", properties.topic());
            dlqPayload.put("partition", null);
            dlqPayload.put("offset", null);
            dlqPayload.put("errorType", error == null ? "UNKNOWN" : error.getClass().getSimpleName());
            dlqPayload.put("errorMessage", error == null ? "unknown kafka publish failure" : error.getMessage());
            dlqPayload.put("originalPayload", originalPayload);
            dlqPayload.put("failedAt", Instant.now());
            dlqPayload.put("eventId", logEvent.eventId());

            String deadLetterJson = objectMapper.writeValueAsString(dlqPayload);
            kafkaTemplate.send(properties.deadLetterTopic(), logEvent.serviceName(), deadLetterJson)
                    .get(properties.kafkaSendTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception ex) {
            if (error != null) {
                ex.addSuppressed(error);
            }
            throw new IllegalStateException("Failed to publish log event to dead letter topic " + properties.deadLetterTopic(), ex);
        }
    }

    private LogEventPayload toPayload(EnrichedLogEvent logEvent) {
        return new LogEventPayload(
                logEvent.eventId(),
                logEvent.serviceName(),
                logEvent.environment(),
                logEvent.timestamp(),
                logEvent.level(),
                logEvent.message(),
                logEvent.exceptionType(),
                logEvent.stackTrace(),
                logEvent.traceId(),
                logEvent.spanId(),
                logEvent.host(),
                logEvent.tags(),
                logEvent.ingestionTimestamp()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize log event", ex);
        }
    }
}
