package com.aiobservability.services.incidentdetectionservice.service;

import com.aiobservability.services.incidentdetectionservice.config.IncidentProperties;
import com.aiobservability.services.incidentdetectionservice.model.IncidentRuleConfig;
import com.aiobservability.services.incidentdetectionservice.model.IncidentRulesUpdateRequest;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class IncidentRuleConfigService {
    private final AtomicReference<IncidentRuleConfig> currentRules;

    public IncidentRuleConfigService(IncidentProperties properties) {
        IncidentProperties.RuleProperties defaults = properties.rules();
        this.currentRules = new AtomicReference<>(new IncidentRuleConfig(
                defaults.correlationWindowMinutes(),
                defaults.errorBurstThreshold(),
                defaults.errorBurstWindowMinutes(),
                defaults.traceFailureThreshold(),
                defaults.traceFailureWindowMinutes()
        ));
    }

    public IncidentRuleConfig current() {
        return currentRules.get();
    }

    public IncidentRuleConfig update(IncidentRulesUpdateRequest request) {
        if (request == null) {
            return current();
        }
        return currentRules.updateAndGet(existing -> {
            int correlationWindowMinutes = mergePositive(
                    request.correlationWindowMinutes(),
                    existing.correlationWindowMinutes(),
                    "correlationWindowMinutes"
            );
            int errorBurstThreshold = mergePositive(
                    request.errorBurstThreshold(),
                    existing.errorBurstThreshold(),
                    "errorBurstThreshold"
            );
            int errorBurstWindowMinutes = mergePositive(
                    request.errorBurstWindowMinutes(),
                    existing.errorBurstWindowMinutes(),
                    "errorBurstWindowMinutes"
            );
            int traceFailureThreshold = mergePositive(
                    request.traceFailureThreshold(),
                    existing.traceFailureThreshold(),
                    "traceFailureThreshold"
            );
            int traceFailureWindowMinutes = mergePositive(
                    request.traceFailureWindowMinutes(),
                    existing.traceFailureWindowMinutes(),
                    "traceFailureWindowMinutes"
            );
            return new IncidentRuleConfig(
                    correlationWindowMinutes,
                    errorBurstThreshold,
                    errorBurstWindowMinutes,
                    traceFailureThreshold,
                    traceFailureWindowMinutes
            );
        });
    }

    public Map<String, Object> summary() {
        IncidentRuleConfig rules = current();
        return Map.of(
                "errorBurst", Map.of(
                        "threshold", rules.errorBurstThreshold(),
                        "windowMinutes", rules.errorBurstWindowMinutes()
                ),
                "traceFailureCluster", Map.of(
                        "threshold", rules.traceFailureThreshold(),
                        "windowMinutes", rules.traceFailureWindowMinutes()
                ),
                "correlationWindowMinutes", rules.correlationWindowMinutes()
        );
    }

    private int mergePositive(Integer update, int existing, String field) {
        if (update == null) {
            return existing;
        }
        if (update <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }
        return update;
    }
}
