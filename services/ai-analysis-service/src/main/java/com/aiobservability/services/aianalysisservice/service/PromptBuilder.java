package com.aiobservability.services.aianalysisservice.service;

import com.aiobservability.services.aianalysisservice.model.IncidentAnalysisPrompt;
import com.aiobservability.services.aianalysisservice.model.IncidentContext;
import com.aiobservability.services.aianalysisservice.model.PromptEvidence;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptBuilder {
    public IncidentAnalysisPrompt build(IncidentContext incident, PromptEvidence evidence) {
        return new IncidentAnalysisPrompt(
                incident.id(),
                incident.severity(),
                incident.affectedServices(),
                evidence.topErrors(),
                evidence.metricBreaches(),
                evidence.traceSummary(),
                compactEvidence(evidence.evidenceLines())
        );
    }

    private List<String> compactEvidence(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of("No detailed evidence collected for this incident.");
        }
        return lines.stream()
                .filter(line -> line != null && !line.isBlank())
                .limit(8)
                .toList();
    }
}
