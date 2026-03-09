package com.aiobservability.services.incidentdetectionservice.model;

public record IncidentRulesUpdateRequest(
        Integer correlationWindowMinutes,
        Integer errorBurstThreshold,
        Integer errorBurstWindowMinutes,
        Integer traceFailureThreshold,
        Integer traceFailureWindowMinutes
) {
}
