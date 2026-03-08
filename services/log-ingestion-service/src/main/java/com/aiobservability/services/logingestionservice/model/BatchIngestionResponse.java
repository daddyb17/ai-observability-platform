package com.aiobservability.services.logingestionservice.model;

import java.util.List;

public record BatchIngestionResponse(
        int accepted,
        int failed,
        List<String> errors
) {
}
