package com.aiobservability.services.aianalysisservice.client;

import com.aiobservability.services.aianalysisservice.model.AiAnalysisResult;
import com.aiobservability.services.aianalysisservice.model.IncidentAnalysisPrompt;

public interface AiClient {
    String provider();

    String modelName();

    AiAnalysisResult analyze(IncidentAnalysisPrompt prompt);
}
