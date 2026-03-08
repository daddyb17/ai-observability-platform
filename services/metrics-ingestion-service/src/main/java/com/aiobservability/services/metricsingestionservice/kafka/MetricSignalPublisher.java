package com.aiobservability.services.metricsingestionservice.kafka;

import com.aiobservability.services.metricsingestionservice.config.MetricsIngestionProperties;
import com.aiobservability.shared.models.EventEnvelope;
import com.aiobservability.shared.models.MetricSignalEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class MetricSignalPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsIngestionProperties properties;

    public MetricSignalPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MetricsIngestionProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void publish(MetricSignalEvent signal) {
        try {
            EventEnvelope<MetricSignalEvent> envelope = new EventEnvelope<>(
                    signal.signalId(),
                    "METRIC_SIGNAL_EVALUATED",
                    "1.0",
                    Instant.now(),
                    "metrics-ingestion-service",
                    signal
            );
            String payload = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(properties.topic(), signal.serviceName(), payload)
                    .get(properties.kafkaSendTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception ex) {
            publishDeadLetter(signal, ex);
            throw new IllegalStateException("Failed to publish metric signal", ex);
        }
    }

    private void publishDeadLetter(MetricSignalEvent signal, Exception error) {
        try {
            Map<String, Object> dlqPayload = new LinkedHashMap<>();
            dlqPayload.put("originalTopic", properties.topic());
            dlqPayload.put("partition", null);
            dlqPayload.put("offset", null);
            dlqPayload.put("errorType", error.getClass().getSimpleName());
            dlqPayload.put("errorMessage", error.getMessage());
            dlqPayload.put("originalPayload", signal);
            dlqPayload.put("failedAt", Instant.now());

            String payload = objectMapper.writeValueAsString(dlqPayload);
            kafkaTemplate.send(properties.deadLetterTopic(), signal.serviceName(), payload)
                    .get(properties.kafkaSendTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }
}
