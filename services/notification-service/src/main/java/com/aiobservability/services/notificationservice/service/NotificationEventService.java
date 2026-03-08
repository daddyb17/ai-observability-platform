package com.aiobservability.services.notificationservice.service;

import com.aiobservability.services.notificationservice.model.AlertMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationEventService {
    private final ObjectMapper objectMapper;
    private final AlertDispatchService alertDispatchService;

    public NotificationEventService(ObjectMapper objectMapper, AlertDispatchService alertDispatchService) {
        this.objectMapper = objectMapper;
        this.alertDispatchService = alertDispatchService;
    }

    public void handleEnvelopeMessage(String topic, String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = root.path("eventType").asText(topic);
            Instant occurredAt = parseInstant(root.path("occurredAt").asText());
            JsonNode payloadNode = root.path("payload");
            Map<String, Object> payload = objectMapper.convertValue(payloadNode, new TypeReference<>() {
            });

            AlertMessage alertMessage = buildAlertMessage(eventType, occurredAt, payload);
            alertDispatchService.dispatch(alertMessage, null);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to process notification event from topic " + topic, ex);
        }
    }

    public AlertMessage fromManualRequest(
            UUID incidentId,
            String severity,
            String title,
            String summary,
            List<String> recommendedActions
    ) {
        if (incidentId == null) {
            throw new IllegalArgumentException("incidentId is required");
        }
        return new AlertMessage(
                incidentId,
                valueOrDefault(severity, "MEDIUM"),
                valueOrDefault(title, "Manual test alert"),
                valueOrDefault(summary, "Manual test notification from notification-service."),
                recommendedActions == null ? List.of() : recommendedActions,
                "MANUAL_TEST",
                Instant.now(),
                Map.of("source", "manual")
        );
    }

    private AlertMessage buildAlertMessage(String eventType, Instant occurredAt, Map<String, Object> payload) {
        UUID incidentId = parseUuid(payload.get("incidentId"));
        String severity = asText(payload.get("severity"));
        String title = asText(payload.get("title"));
        String summary = asText(payload.get("summary"));

        if (summary == null || summary.isBlank()) {
            if (eventType.contains("INCIDENT")) {
                summary = "Incident event received with status " + valueOrDefault(asText(payload.get("status")), "UNKNOWN");
            } else if (eventType.contains("AI_ANALYSIS")) {
                summary = valueOrDefault(asText(payload.get("rootCause")), "AI analysis completed.");
            } else {
                summary = "Alert event received from " + eventType;
            }
        }

        List<String> recommendedActions = toStringList(payload.get("recommendedActions"));
        return new AlertMessage(
                incidentId,
                valueOrDefault(severity, "MEDIUM"),
                valueOrDefault(title, "Incident alert"),
                summary,
                recommendedActions,
                eventType,
                occurredAt == null ? Instant.now() : occurredAt,
                payload
        );
    }

    private UUID parseUuid(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("incidentId is missing in notification event payload");
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (Exception ex) {
            throw new IllegalArgumentException("incidentId is invalid UUID in notification event payload");
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }
}
