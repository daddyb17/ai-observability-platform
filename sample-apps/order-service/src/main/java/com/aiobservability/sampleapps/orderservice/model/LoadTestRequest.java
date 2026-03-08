package com.aiobservability.sampleapps.orderservice.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record LoadTestRequest(
        @Min(1) @Max(500) int count
) {
}
