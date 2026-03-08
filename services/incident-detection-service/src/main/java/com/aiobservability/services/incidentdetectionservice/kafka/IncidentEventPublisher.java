package com.aiobservability.services.incidentdetectionservice.kafka;

import com.aiobservability.services.incidentdetectionservice.config.IncidentProperties;
import com.aiobservability.services.incidentdetectionservice.model.IncidentRecord;
import com.aiobservability.shared.models.EventEnvelope;
import com.aiobservability.shared.models.IncidentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class IncidentEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final IncidentProperties properties;

    public IncidentEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            IncidentProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void publishDetected(IncidentRecord incident) {
        publishIncident(properties.kafka().topicIncidentsDetected(), "INCIDENT_DETECTED", incident);
    }

    public void publishUpdated(IncidentRecord incident) {
        publishIncident(properties.kafka().topicIncidentsUpdated(), "INCIDENT_UPDATED", incident);
    }

    public void publishAnalysisRequest(String incidentId) {
        Map<String, Object> payload = Map.of(
                "incidentId", incidentId,
                "requestedAt", Instant.now().toString()
        );
        publishGeneric(properties.kafka().topicAiAnalysisRequest(), incidentId, "AI_ANALYSIS_REQUESTED", payload);
    }

    public void publishAlertOutbound(IncidentRecord incident) {
        Map<String, Object> payload = Map.of(
                "incidentId", incident.id().toString(),
                "title", incident.title(),
                "severity", incident.severity().name(),
                "status", incident.status().name(),
                "affectedServices", incident.affectedServices()
        );
        publishGeneric(properties.kafka().topicAlertsOutbound(), incident.id().toString(), "ALERT_OUTBOUND_REQUESTED", payload);
    }

    private void publishIncident(String topic, String eventType, IncidentRecord incident) {
        IncidentEvent payload = new IncidentEvent(
                incident.id().toString(),
                incident.title(),
                incident.severity(),
                incident.status(),
                incident.affectedServices(),
                incident.dominantSignalType(),
                incident.updatedAt()
        );
        publishGeneric(topic, incident.id().toString(), eventType, payload);
    }

    private void publishGeneric(String topic, String key, String eventType, Object payload) {
        EventEnvelope<Object> envelope = new EventEnvelope<>(
                key,
                eventType,
                "1.0",
                Instant.now(),
                "incident-detection-service",
                payload
        );
        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, key, json).get(properties.kafka().sendTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception ex) {
            publishDeadLetter(topic, key, payload, ex);
            throw new IllegalStateException("Failed to publish event to topic " + topic, ex);
        }
    }

    private void publishDeadLetter(String originalTopic, String key, Object payload, Exception error) {
        Map<String, Object> dlqPayload = new LinkedHashMap<>();
        dlqPayload.put("originalTopic", originalTopic);
        dlqPayload.put("partition", null);
        dlqPayload.put("offset", null);
        dlqPayload.put("errorType", error.getClass().getSimpleName());
        dlqPayload.put("errorMessage", error.getMessage());
        dlqPayload.put("originalPayload", payload);
        dlqPayload.put("failedAt", Instant.now());
        try {
            String json = objectMapper.writeValueAsString(dlqPayload);
            kafkaTemplate.send("deadletter.incidents", key, json)
                    .get(properties.kafka().sendTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }
}
