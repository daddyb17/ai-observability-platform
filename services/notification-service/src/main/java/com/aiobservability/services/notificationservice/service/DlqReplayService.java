package com.aiobservability.services.notificationservice.service;

import com.aiobservability.services.notificationservice.config.NotificationProperties;
import com.aiobservability.services.notificationservice.model.DlqReplayRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class DlqReplayService {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;

    public DlqReplayService(
            KafkaTemplate<String, String> kafkaTemplate,
            NotificationProperties properties,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> replay(DlqReplayRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }

        ReplayPayload payload = resolvePayload(request);
        if (payload.originalTopic() == null || payload.originalTopic().isBlank()) {
            throw new IllegalArgumentException("originalTopic is required");
        }
        if (payload.originalPayload() == null || payload.originalPayload().isBlank()) {
            throw new IllegalArgumentException("originalPayload is required");
        }

        String key = payload.key() == null || payload.key().isBlank()
                ? "dlq-replay"
                : payload.key();
        try {
            kafkaTemplate.send(payload.originalTopic(), key, payload.originalPayload())
                    .get(properties.kafka().sendTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to replay DLQ payload to topic " + payload.originalTopic(), ex);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "replayed");
        response.put("originalTopic", payload.originalTopic());
        response.put("key", key);
        return response;
    }

    private ReplayPayload resolvePayload(DlqReplayRequest request) {
        if (request.dlqMessage() == null || request.dlqMessage().isBlank()) {
            return new ReplayPayload(request.originalTopic(), request.originalPayload(), request.key());
        }
        try {
            JsonNode root = objectMapper.readTree(request.dlqMessage());
            String originalTopic = root.path("originalTopic").asText(request.originalTopic());
            JsonNode originalPayloadNode = root.path("originalPayload");
            String originalPayload = request.originalPayload();
            if (originalPayloadNode.isTextual()) {
                originalPayload = originalPayloadNode.asText();
            } else if (!originalPayloadNode.isMissingNode() && !originalPayloadNode.isNull()) {
                originalPayload = objectMapper.writeValueAsString(originalPayloadNode);
            }
            return new ReplayPayload(originalTopic, originalPayload, request.key());
        } catch (Exception ex) {
            throw new IllegalArgumentException("dlqMessage is not valid JSON", ex);
        }
    }

    private record ReplayPayload(
            String originalTopic,
            String originalPayload,
            String key
    ) {
    }
}
