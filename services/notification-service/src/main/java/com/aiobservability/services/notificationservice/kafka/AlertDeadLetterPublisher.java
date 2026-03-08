package com.aiobservability.services.notificationservice.kafka;

import com.aiobservability.services.notificationservice.config.NotificationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AlertDeadLetterPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationProperties properties;

    public AlertDeadLetterPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            NotificationProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void publish(String originalTopic, String originalPayload, Exception error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("originalTopic", originalTopic);
        payload.put("partition", null);
        payload.put("offset", null);
        payload.put("errorType", error.getClass().getSimpleName());
        payload.put("errorMessage", error.getMessage());
        payload.put("originalPayload", originalPayload);
        payload.put("failedAt", Instant.now());
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(
                            properties.kafka().deadLetterTopic(),
                            originalTopic,
                            json
                    )
                    .get(properties.kafka().sendTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }
}
