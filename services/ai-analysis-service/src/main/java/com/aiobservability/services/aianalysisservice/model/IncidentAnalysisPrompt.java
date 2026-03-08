package com.aiobservability.services.aianalysisservice.model;

import java.util.List;
import java.util.UUID;

public record IncidentAnalysisPrompt(
        UUID incidentId,
        String severity,
        List<String> affectedServices,
        List<PromptEvidence.TopError> topErrors,
        List<PromptEvidence.MetricBreach> metricBreaches,
        TraceSummaryRecord traceSummary,
        List<String> evidence
) {
}
