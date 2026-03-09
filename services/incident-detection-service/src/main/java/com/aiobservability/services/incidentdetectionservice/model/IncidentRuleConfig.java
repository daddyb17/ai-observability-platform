package com.aiobservability.services.incidentdetectionservice.model;

public record IncidentRuleConfig(
        int correlationWindowMinutes,
        int errorBurstThreshold,
        int errorBurstWindowMinutes,
        int traceFailureThreshold,
        int traceFailureWindowMinutes
) {
}
