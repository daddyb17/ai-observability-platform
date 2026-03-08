package com.aiobservability.services.incidentdetectionservice.service;

import com.aiobservability.shared.models.Severity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeverityCalculator {
    public Severity calculate(List<String> signalTypes) {
        int score = 0;
        for (String signalType : signalTypes) {
            score += weight(signalType);
        }
        if (score >= 80) {
            return Severity.CRITICAL;
        }
        if (score >= 50) {
            return Severity.HIGH;
        }
        if (score >= 25) {
            return Severity.MEDIUM;
        }
        return Severity.LOW;
    }

    private int weight(String signalType) {
        return switch (signalType) {
            case "LOG_EXCEPTION_BURST" -> 20;
            case "METRIC_LATENCY_BREACH" -> 20;
            case "METRIC_ERROR_RATE_BREACH" -> 30;
            case "TRACE_FAILURE_CLUSTER" -> 15;
            case "CASCADING_FAILURE" -> 20;
            case "NOTIFICATION_FAILURE" -> 5;
            default -> 0;
        };
    }
}
