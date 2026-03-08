package com.aiobservability.services.aianalysisservice.kafka;

import com.aiobservability.services.aianalysisservice.config.AiProperties;
import com.aiobservability.services.aianalysisservice.model.AiAnalysisRecord;
import com.aiobservability.shared.models.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AiAnalysisResultPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;

    public AiAnalysisResultPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            AiProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void publish(AiAnalysisRecord analysis) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("incidentId", analysis.incidentId().toString());
        payload.put("summary", analysis.summary());
        payload.put("rootCause", analysis.rootCause());
        payload.put("confidence", analysis.confidence());
        payload.put("recommendedActions", analysis.recommendedActions());
        payload.put("evidence", analysis.evidence());
        payload.put("generatedAt", analysis.createdAt());

        EventEnvelope<Object> envelope = new EventEnvelope<>(
                analysis.id().toString(),
                "AI_ANALYSIS_RESULT",
                "1.0",
                Instant.now(),
                "ai-analysis-service",
                payload
        );
        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(
                            properties.kafka().topicAnalysisResult(),
                            analysis.incidentId().toString(),
                            json
                    )
                    .get(properties.kafka().sendTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception ex) {
            publishDeadLetter(analysis, ex);
            throw new IllegalStateException("Failed to publish ai.analysis.result", ex);
        }
    }

    private void publishDeadLetter(AiAnalysisRecord analysis, Exception error) {
        Map<String, Object> dlqPayload = new LinkedHashMap<>();
        dlqPayload.put("originalTopic", properties.kafka().topicAnalysisResult());
        dlqPayload.put("partition", null);
        dlqPayload.put("offset", null);
        dlqPayload.put("errorType", error.getClass().getSimpleName());
        dlqPayload.put("errorMessage", error.getMessage());
        dlqPayload.put("originalPayload", analysis);
        dlqPayload.put("failedAt", Instant.now());
        try {
            String json = objectMapper.writeValueAsString(dlqPayload);
            kafkaTemplate.send(properties.kafka().deadLetterTopic(), analysis.incidentId().toString(), json)
                    .get(properties.kafka().sendTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }
}
