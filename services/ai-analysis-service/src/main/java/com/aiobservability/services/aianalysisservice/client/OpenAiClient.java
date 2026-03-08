package com.aiobservability.services.aianalysisservice.client;

import com.aiobservability.services.aianalysisservice.config.AiProperties;
import com.aiobservability.services.aianalysisservice.model.AiAnalysisResult;
import com.aiobservability.services.aianalysisservice.model.IncidentAnalysisPrompt;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiClient implements AiClient {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return "openai";
    }

    @Override
    public String modelName() {
        return properties.openai().model();
    }

    @Override
    public AiAnalysisResult analyze(IncidentAnalysisPrompt prompt) {
        if (properties.openai().apiKey() == null || properties.openai().apiKey().isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is empty");
        }

        RestClient restClient = RestClient.builder()
                .baseUrl(properties.openai().baseUrl())
                .build();

        String userPrompt = buildPrompt(prompt);
        Map<String, Object> requestBody = Map.of(
                "model", modelName(),
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "You are an incident analyst. Reply only JSON with keys: summary, rootCause, confidence, recommendedActions, evidence."),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "response_format", Map.of("type", "json_object")
        );

        String response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.openai().apiKey())
                .body(requestBody)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode structured = objectMapper.readTree(content);
            String summary = structured.path("summary").asText("Incident analysis summary unavailable.");
            String rootCause = structured.path("rootCause").asText("Root cause is inconclusive.");
            double confidence = structured.path("confidence").asDouble(0.65);
            List<String> recommendedActions = objectMapper.convertValue(
                    structured.path("recommendedActions"),
                    new TypeReference<>() {
                    }
            );
            List<String> evidence = objectMapper.convertValue(
                    structured.path("evidence"),
                    new TypeReference<>() {
                    }
            );
            Map<String, Object> raw = objectMapper.convertValue(root, new TypeReference<>() {
            });
            return new AiAnalysisResult(
                    summary,
                    rootCause,
                    confidence,
                    recommendedActions == null ? List.of() : recommendedActions,
                    evidence == null ? List.of() : evidence,
                    provider(),
                    modelName(),
                    raw,
                    false
            );
        } catch (Exception ex) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("response", response);
            raw.put("parseError", ex.getMessage());
            throw new IllegalStateException("Failed to parse OpenAI response", ex);
        }
    }

    private String buildPrompt(IncidentAnalysisPrompt prompt) {
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("incidentId", prompt.incidentId());
        compact.put("severity", prompt.severity());
        compact.put("affectedServices", prompt.affectedServices());
        compact.put("topErrors", prompt.topErrors());
        compact.put("metricBreaches", prompt.metricBreaches());
        compact.put("traceSummary", prompt.traceSummary());
        compact.put("evidence", prompt.evidence());
        try {
            return objectMapper.writeValueAsString(compact);
        } catch (Exception ex) {
            return compact.toString();
        }
    }
}
