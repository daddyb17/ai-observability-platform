package com.aiobservability.services.aianalysisservice.model;

import java.util.List;

public record PromptEvidence(
        List<TopError> topErrors,
        List<MetricBreach> metricBreaches,
        TraceSummaryRecord traceSummary,
        List<String> evidenceLines
) {
    public record TopError(String message, long count) {
    }

    public record MetricBreach(String metric, double value, double threshold) {
    }
}
