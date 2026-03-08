package com.aiobservability.services.aianalysisservice.consumer;

import com.aiobservability.services.aianalysisservice.config.AiProperties;
import com.aiobservability.services.aianalysisservice.service.AiAnalysisService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AiAnalysisRequestConsumer {
    private final ObjectMapper objectMapper;
    private final AiAnalysisService aiAnalysisService;
    private final AiProperties properties;

    public AiAnalysisRequestConsumer(
            ObjectMapper objectMapper,
            AiAnalysisService aiAnalysisService,
            AiProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.aiAnalysisService = aiAnalysisService;
        this.properties = properties;
    }

    @KafkaListener(
            topics = "${app.ai.kafka.topic-analysis-request:ai.analysis.request}",
            groupId = "${app.ai.kafka.consumer-group-id:ai-analysis-service}"
    )
    public void consume(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.path("payload");
            String incidentIdText = payload.path("incidentId").asText();
            if (incidentIdText == null || incidentIdText.isBlank()) {
                throw new IllegalArgumentException("incidentId is missing in ai.analysis.request payload");
            }
            UUID incidentId = UUID.fromString(incidentIdText);
            aiAnalysisService.analyzeIncident(incidentId, "kafka");
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to process message from topic " + properties.kafka().topicAnalysisRequest(),
                    ex
            );
        }
    }
}
