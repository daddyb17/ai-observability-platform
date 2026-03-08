package com.aiobservability.services.incidentdetectionservice.model;

import com.aiobservability.shared.models.IncidentStatus;

public record IncidentStatusUpdateRequest(
        IncidentStatus status
) {
}
