package com.aiobservability.services.incidentdetectionservice.service;

import com.aiobservability.services.incidentdetectionservice.config.IncidentProperties;
import com.aiobservability.services.incidentdetectionservice.model.IncidentCandidate;
import com.aiobservability.shared.models.LogEventPayload;
import com.aiobservability.shared.models.MetricSignalEvent;
import com.aiobservability.shared.models.TraceSummaryEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IncidentRuleEngine {
    private final IncidentProperties properties;
    private final IncidentService incidentService;

    private final Map<String, Deque<Instant>> errorBursts = new ConcurrentHashMap<>();
    private final Map<String, Instant> errorBurstLastTriggered = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> traceFailureClusters = new ConcurrentHashMap<>();
    private final Map<String, Instant> traceFailureLastTriggered = new ConcurrentHashMap<>();
    private final Map<String, Instant> recentServiceBreaches = new ConcurrentHashMap<>();
    private final Map<String, Instant> cascadeLastTriggered = new ConcurrentHashMap<>();

    public IncidentRuleEngine(IncidentProperties properties, IncidentService incidentService) {
        this.properties = properties;
        this.incidentService = incidentService;
    }

    public void onLog(LogEventPayload logEvent) {
        if (logEvent == null || logEvent.serviceName() == null || logEvent.level() == null) {
            return;
        }
        if (!"ERROR".equalsIgnoreCase(logEvent.level()) || logEvent.exceptionType() == null || logEvent.exceptionType().isBlank()) {
            return;
        }
        Instant observedAt = logEvent.timestamp() == null ? Instant.now() : logEvent.timestamp();
        String key = logEvent.serviceName() + ":" + logEvent.exceptionType();
        trackBurst(errorBursts, key, observedAt, properties.rules().errorBurstWindowMinutes());

        if (errorBursts.getOrDefault(key, new ArrayDeque<>()).size() >= properties.rules().errorBurstThreshold()
                && shouldTrigger(errorBurstLastTriggered, key, observedAt)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("serviceName", logEvent.serviceName());
            payload.put("exceptionType", logEvent.exceptionType());
            payload.put("message", logEvent.message());
            payload.put("traceId", logEvent.traceId());

            IncidentCandidate candidate = new IncidentCandidate(
                    logEvent.serviceName() + " exception burst",
                    "Repeated " + logEvent.exceptionType() + " errors detected in " + logEvent.serviceName(),
                    "LOG_EXCEPTION_BURST",
                    key,
                    20,
                    List.of(logEvent.serviceName()),
                    logEvent.traceId(),
                    payload,
                    observedAt
            );
            incidentService.processCandidate(candidate);
            registerBreach(logEvent.serviceName(), observedAt);
        }
    }

    public void onMetric(MetricSignalEvent metricSignalEvent) {
        if (metricSignalEvent == null || !"BREACHED".equalsIgnoreCase(metricSignalEvent.status())) {
            return;
        }
        Instant observedAt = metricSignalEvent.timestamp() == null ? Instant.now() : metricSignalEvent.timestamp();
        String signalType;
        int weight;
        if (metricSignalEvent.metricName() != null && metricSignalEvent.metricName().contains("error_rate")) {
            signalType = "METRIC_ERROR_RATE_BREACH";
            weight = 30;
        } else if (metricSignalEvent.metricName() != null && metricSignalEvent.metricName().contains("latency")) {
            signalType = "METRIC_LATENCY_BREACH";
            weight = 20;
        } else {
            return;
        }

        String signalKey = metricSignalEvent.serviceName() + ":" + metricSignalEvent.metricName();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("metricName", metricSignalEvent.metricName());
        payload.put("value", metricSignalEvent.value());
        payload.put("threshold", metricSignalEvent.threshold());
        payload.put("window", metricSignalEvent.window());

        IncidentCandidate candidate = new IncidentCandidate(
                metricSignalEvent.serviceName() + " metric breach",
                "Metric " + metricSignalEvent.metricName() + " breached threshold for " + metricSignalEvent.serviceName(),
                signalType,
                signalKey,
                weight,
                List.of(metricSignalEvent.serviceName()),
                null,
                payload,
                observedAt
        );
        incidentService.processCandidate(candidate);
        registerBreach(metricSignalEvent.serviceName(), observedAt);
        maybeTriggerCascade(observedAt);
    }

    public void onTrace(TraceSummaryEvent traceSummaryEvent) {
        if (traceSummaryEvent == null || !traceSummaryEvent.errorFlag() || traceSummaryEvent.bottleneckService() == null) {
            return;
        }
        Instant observedAt = traceSummaryEvent.startedAt() == null ? Instant.now() : traceSummaryEvent.startedAt();
        String key = traceSummaryEvent.bottleneckService();
        trackBurst(traceFailureClusters, key, observedAt, properties.rules().traceFailureWindowMinutes());

        if (traceFailureClusters.getOrDefault(key, new ArrayDeque<>()).size() >= properties.rules().traceFailureThreshold()
                && shouldTrigger(traceFailureLastTriggered, key, observedAt)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("traceId", traceSummaryEvent.traceId());
            payload.put("rootService", traceSummaryEvent.rootService());
            payload.put("bottleneckService", traceSummaryEvent.bottleneckService());
            payload.put("durationMs", traceSummaryEvent.durationMs());

            IncidentCandidate candidate = new IncidentCandidate(
                    "Trace failure cluster at " + traceSummaryEvent.bottleneckService(),
                    "Repeated failing traces with bottleneck at " + traceSummaryEvent.bottleneckService(),
                    "TRACE_FAILURE_CLUSTER",
                    "trace-bottleneck:" + traceSummaryEvent.bottleneckService(),
                    15,
                    List.of(traceSummaryEvent.rootService(), traceSummaryEvent.bottleneckService()),
                    traceSummaryEvent.traceId(),
                    payload,
                    observedAt
            );
            incidentService.processCandidate(candidate);
            registerBreach(traceSummaryEvent.bottleneckService(), observedAt);
            maybeTriggerCascade(observedAt);
        }
    }

    private void maybeTriggerCascade(Instant observedAt) {
        Instant orderBreach = recentServiceBreaches.get("order-service");
        Instant paymentBreach = recentServiceBreaches.get("payment-service");
        if (orderBreach == null || paymentBreach == null) {
            return;
        }
        Instant cutoff = observedAt.minus(properties.rules().correlationWindowMinutes(), ChronoUnit.MINUTES);
        if (orderBreach.isBefore(cutoff) || paymentBreach.isBefore(cutoff)) {
            return;
        }
        if (!shouldTrigger(cascadeLastTriggered, "order-payment", observedAt)) {
            return;
        }

        Map<String, Object> payload = Map.of(
                "services", List.of("order-service", "payment-service"),
                "orderBreachAt", orderBreach.toString(),
                "paymentBreachAt", paymentBreach.toString()
        );
        IncidentCandidate candidate = new IncidentCandidate(
                "Cascading failure between order-service and payment-service",
                "Correlated breaches detected across order and payment services.",
                "CASCADING_FAILURE",
                "cascade:order-payment",
                20,
                List.of("order-service", "payment-service"),
                null,
                payload,
                observedAt
        );
        incidentService.processCandidate(candidate);
    }

    private void registerBreach(String serviceName, Instant observedAt) {
        if (serviceName != null && !serviceName.isBlank()) {
            recentServiceBreaches.put(serviceName, observedAt);
        }
    }

    private void trackBurst(Map<String, Deque<Instant>> store, String key, Instant observedAt, int windowMinutes) {
        Deque<Instant> deque = store.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        deque.addLast(observedAt);
        Instant cutoff = observedAt.minus(windowMinutes, ChronoUnit.MINUTES);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
            deque.pollFirst();
        }
    }

    private boolean shouldTrigger(Map<String, Instant> lastTriggeredMap, String key, Instant now) {
        Instant lastTriggered = lastTriggeredMap.get(key);
        if (lastTriggered != null && lastTriggered.isAfter(now.minus(1, ChronoUnit.MINUTES))) {
            return false;
        }
        lastTriggeredMap.put(key, now);
        return true;
    }
}
