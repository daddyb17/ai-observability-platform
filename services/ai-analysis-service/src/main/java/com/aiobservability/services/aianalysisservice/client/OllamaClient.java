package com.aiobservability.services.aianalysisservice.client;

import com.aiobservability.services.aianalysisservice.config.AiProperties;
import com.aiobservability.services.aianalysisservice.model.AiAnalysisResult;
import com.aiobservability.services.aianalysisservice.model.IncidentAnalysisPrompt;
import org.springframework.stereotype.Component;

@Component
public class OllamaClient implements AiClient {
    private final AiProperties properties;

    public OllamaClient(AiProperties properties) {
        this.properties = properties;
    }

    @Override
    public String provider() {
        return "ollama";
    }

    @Override
    public String modelName() {
        return properties.ollama().model();
    }

    @Override
    public AiAnalysisResult analyze(IncidentAnalysisPrompt prompt) {
        throw new UnsupportedOperationException("Ollama provider is not implemented yet");
    }
}
