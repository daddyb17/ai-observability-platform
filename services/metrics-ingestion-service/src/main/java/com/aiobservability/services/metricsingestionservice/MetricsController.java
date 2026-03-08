package com.aiobservability.services.metricsingestionservice;

import com.aiobservability.services.metricsingestionservice.model.MetricsEvaluationResponse;
import com.aiobservability.services.metricsingestionservice.model.ServiceMetricSnapshot;
import com.aiobservability.services.metricsingestionservice.service.MetricSignalStore;
import com.aiobservability.services.metricsingestionservice.service.MetricsEvaluationService;
import com.aiobservability.services.metricsingestionservice.service.MetricsRuleService;
import com.aiobservability.shared.models.MetricSignalEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class MetricsController {
    private final MetricsEvaluationService metricsEvaluationService;
    private final MetricsRuleService metricsRuleService;
    private final MetricSignalStore metricSignalStore;

    public MetricsController(
            MetricsEvaluationService metricsEvaluationService,
            MetricsRuleService metricsRuleService,
            MetricSignalStore metricSignalStore
    ) {
        this.metricsEvaluationService = metricsEvaluationService;
        this.metricsRuleService = metricsRuleService;
        this.metricSignalStore = metricSignalStore;
    }

    @GetMapping("/api/metrics/services/{serviceName}")
    public ResponseEntity<Map<String, Object>> metricsByService(@PathVariable("serviceName") String serviceName) {
        ServiceMetricSnapshot snapshot = metricsEvaluationService.evaluateCurrentMetrics(serviceName);
        List<MetricSignalEvent> signals = metricSignalStore.getByService(serviceName);
        return ResponseEntity.ok(Map.of(
                "serviceName", serviceName,
                "evaluatedAt", snapshot.evaluatedAt(),
                "currentMetrics", snapshot.metrics(),
                "breachedSignals", signals
        ));
    }

    @GetMapping("/api/metrics/incidents/{incidentId}")
    public ResponseEntity<Map<String, Object>> metricsByIncident(@PathVariable("incidentId") String incidentId) {
        List<MetricSignalEvent> signals = metricSignalStore.getForIncident(incidentId);
        return ResponseEntity.ok(Map.of(
                "incidentId", incidentId,
                "signals", signals
        ));
    }

    @GetMapping("/internal/metrics/rules")
    public ResponseEntity<Map<String, Object>> rules() {
        return ResponseEntity.ok(Map.of(
                "rules", metricsRuleService.getRules()
        ));
    }

    @PostMapping("/internal/metrics/evaluate")
    public ResponseEntity<MetricsEvaluationResponse> evaluate() {
        MetricsEvaluationResponse response = metricsEvaluationService.evaluateNow();
        return ResponseEntity.accepted().body(response);
    }
}
