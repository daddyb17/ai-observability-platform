package com.aiobservability.services.incidentdetectionservice.consumer;

import com.aiobservability.services.incidentdetectionservice.service.IncidentRuleEngine;
import com.aiobservability.shared.models.LogEventPayload;
import com.aiobservability.shared.models.MetricSignalEvent;
import com.aiobservability.shared.models.TraceSummaryEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class IncidentSignalConsumer {
    private final ObjectMapper objectMapper;
    private final IncidentRuleEngine incidentRuleEngine;

    public IncidentSignalConsumer(ObjectMapper objectMapper, IncidentRuleEngine incidentRuleEngine) {
        this.objectMapper = objectMapper;
        this.incidentRuleEngine = incidentRuleEngine;
    }

    @KafkaListener(topics = "logs.raw", groupId = "incident-detection-service")
    public void onLog(String message) {
        JsonNode payloadNode = extractPayloadNode(message);
        if (payloadNode == null) {
            return;
        }
        LogEventPayload payload = objectMapper.convertValue(payloadNode, LogEventPayload.class);
        incidentRuleEngine.onLog(payload);
    }

    @KafkaListener(topics = "metrics.raw", groupId = "incident-detection-service")
    public void onMetric(String message) {
        JsonNode payloadNode = extractPayloadNode(message);
        if (payloadNode == null) {
            return;
        }
        MetricSignalEvent payload = objectMapper.convertValue(payloadNode, MetricSignalEvent.class);
        incidentRuleEngine.onMetric(payload);
    }

    @KafkaListener(topics = "traces.raw", groupId = "incident-detection-service")
    public void onTrace(String message) {
        JsonNode payloadNode = extractPayloadNode(message);
        if (payloadNode == null) {
            return;
        }
        TraceSummaryEvent payload = objectMapper.convertValue(payloadNode, TraceSummaryEvent.class);
        incidentRuleEngine.onTrace(payload);
    }

    private JsonNode extractPayloadNode(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.path("payload");
            if (payload.isMissingNode() || payload.isNull()) {
                return null;
            }
            return payload;
        } catch (Exception ex) {
            return null;
        }
    }
}
