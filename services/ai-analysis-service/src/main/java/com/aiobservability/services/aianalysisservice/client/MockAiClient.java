package com.aiobservability.services.aianalysisservice.client;

import com.aiobservability.services.aianalysisservice.model.AiAnalysisResult;
import com.aiobservability.services.aianalysisservice.model.IncidentAnalysisPrompt;
import com.aiobservability.services.aianalysisservice.model.PromptEvidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MockAiClient implements AiClient {
    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String modelName() {
        return "mock-rule-based-v1";
    }

    @Override
    public AiAnalysisResult analyze(IncidentAnalysisPrompt prompt) {
        String dominantService = prompt.affectedServices().isEmpty()
                ? "unknown-service"
                : prompt.affectedServices().get(0);
        PromptEvidence.TopError topError = prompt.topErrors().isEmpty()
                ? null
                : prompt.topErrors().get(0);
        PromptEvidence.MetricBreach breach = prompt.metricBreaches().isEmpty()
                ? null
                : prompt.metricBreaches().get(0);

        String summary;
        if (topError != null) {
            summary = dominantService + " is experiencing repeated errors: " + topError.message() + ".";
        } else if (breach != null) {
            summary = dominantService + " breached metric threshold for " + breach.metric() + ".";
        } else {
            summary = dominantService + " is showing abnormal behavior based on correlated incident signals.";
        }

        String rootCause;
        if (topError != null && topError.message().toLowerCase().contains("timeout")) {
            rootCause = "Likely downstream dependency timeout or exhausted connection pool.";
        } else if (breach != null && breach.metric().toLowerCase().contains("latency")) {
            rootCause = "Likely saturation or downstream slowness causing latency amplification.";
        } else {
            rootCause = "Likely localized service degradation requiring metrics and trace verification.";
        }

        List<String> recommendedActions = new ArrayList<>();
        recommendedActions.add("Inspect " + dominantService + " error logs and recent deployments");
        recommendedActions.add("Check JVM and request latency metrics for the affected service");
        recommendedActions.add("Trace failing requests to identify the bottleneck dependency");

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("provider", provider());
        raw.put("model", modelName());
        raw.put("reasoningType", "deterministic-mock");

        return new AiAnalysisResult(
                summary,
                rootCause,
                0.82,
                recommendedActions,
                prompt.evidence(),
                provider(),
                modelName(),
                raw,
                false
        );
    }
}
