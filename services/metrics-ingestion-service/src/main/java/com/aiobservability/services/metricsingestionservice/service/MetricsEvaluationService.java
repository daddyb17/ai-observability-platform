package com.aiobservability.services.metricsingestionservice.service;

import com.aiobservability.services.metricsingestionservice.config.MetricsIngestionProperties;
import com.aiobservability.services.metricsingestionservice.model.MetricsEvaluationResponse;
import com.aiobservability.services.metricsingestionservice.model.ServiceMetricSnapshot;
import com.aiobservability.services.metricsingestionservice.kafka.MetricSignalPublisher;
import com.aiobservability.shared.models.MetricSignalEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

@Service
public class MetricsEvaluationService {
    private final MetricsIngestionProperties properties;
    private final PrometheusQueryClient prometheusQueryClient;
    private final MetricSignalPublisher metricSignalPublisher;
    private final MetricSignalStore metricSignalStore;

    public MetricsEvaluationService(
            MetricsIngestionProperties properties,
            PrometheusQueryClient prometheusQueryClient,
            MetricSignalPublisher metricSignalPublisher,
            MetricSignalStore metricSignalStore
    ) {
        this.properties = properties;
        this.prometheusQueryClient = prometheusQueryClient;
        this.metricSignalPublisher = metricSignalPublisher;
        this.metricSignalStore = metricSignalStore;
    }

    @Scheduled(fixedDelayString = "${app.metrics-ingestion.evaluate-fixed-delay-ms:30000}")
    public void scheduledEvaluate() {
        evaluateNow();
    }

    public MetricsEvaluationResponse evaluateNow() {
        Instant now = Instant.now();
        List<MetricSignalEvent> breachedSignals = new ArrayList<>();
        for (MetricsIngestionProperties.MonitoredService service : properties.services()) {
            ServiceMetricSnapshot snapshot = evaluateService(service, now);
            breachedSignals.addAll(toBreaches(snapshot, service, now));
        }
        breachedSignals.forEach(metricSignalStore::add);
        breachedSignals.forEach(metricSignalPublisher::publish);
        return new MetricsEvaluationResponse(now, properties.services().size(), breachedSignals.size(), breachedSignals);
    }

    public ServiceMetricSnapshot evaluateCurrentMetrics(String serviceName) {
        MetricsIngestionProperties.MonitoredService service = findService(serviceName);
        if (service == null) {
            return new ServiceMetricSnapshot(serviceName, Instant.now(), Map.of());
        }
        return evaluateService(service, Instant.now());
    }

    private ServiceMetricSnapshot evaluateService(MetricsIngestionProperties.MonitoredService service, Instant now) {
        String instanceRegex = service.instanceRegex();
        String window = properties.evaluationWindow();

        double errorRate = query("sum(rate(http_server_requests_seconds_count{instance=~\"%s\",status=~\"5..\"}[%s])) / clamp_min(sum(rate(http_server_requests_seconds_count{instance=~\"%s\"}[%s])), 0.000001)",
                instanceRegex, window, instanceRegex, window);
        double latencyP95Ms = query("histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{instance=~\"%s\"}[%s])) by (le)) * 1000",
                instanceRegex, window);
        double jvmHeap = query("sum(jvm_memory_used_bytes{instance=~\"%s\",area=\"heap\"}) / clamp_min(sum(jvm_memory_max_bytes{instance=~\"%s\",area=\"heap\"}), 1)",
                instanceRegex, instanceRegex);
        double dbPoolUsage = query("sum(hikaricp_connections_active{instance=~\"%s\"}) / clamp_min(sum(hikaricp_connections_max{instance=~\"%s\"}), 1)",
                instanceRegex, instanceRegex);

        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("http_server_requests_error_rate", errorRate);
        metrics.put("http_server_requests_p95_latency_ms", latencyP95Ms);
        metrics.put("jvm_heap_usage_ratio", jvmHeap);
        metrics.put("db_pool_usage_ratio", dbPoolUsage);
        return new ServiceMetricSnapshot(service.serviceName(), now, metrics);
    }

    private List<MetricSignalEvent> toBreaches(
            ServiceMetricSnapshot snapshot,
            MetricsIngestionProperties.MonitoredService service,
            Instant now
    ) {
        MetricsIngestionProperties.RuleProperties rules = properties.rules();
        List<MetricSignalEvent> signals = new ArrayList<>();
        addIfBreached(signals, service.serviceName(), "http_server_requests_error_rate",
                snapshot.metrics().get("http_server_requests_error_rate"), rules.errorRateThreshold(), now);
        addIfBreached(signals, service.serviceName(), "http_server_requests_p95_latency_ms",
                snapshot.metrics().get("http_server_requests_p95_latency_ms"), rules.latencyP95ThresholdMs(), now);
        addIfBreached(signals, service.serviceName(), "jvm_heap_usage_ratio",
                snapshot.metrics().get("jvm_heap_usage_ratio"), rules.jvmHeapThreshold(), now);
        addIfBreached(signals, service.serviceName(), "db_pool_usage_ratio",
                snapshot.metrics().get("db_pool_usage_ratio"), rules.dbConnectionsThreshold(), now);
        return signals;
    }

    private void addIfBreached(
            List<MetricSignalEvent> signals,
            String serviceName,
            String metricName,
            Double value,
            double threshold,
            Instant now
    ) {
        if (value == null || !Double.isFinite(value) || value <= threshold) {
            return;
        }
        signals.add(new MetricSignalEvent(
                UUID.randomUUID().toString(),
                serviceName,
                metricName,
                value,
                threshold,
                "BREACHED",
                properties.evaluationWindow(),
                now
        ));
    }

    private double query(String template, Object... args) {
        String promQl = template.formatted(args);
        OptionalDouble value = prometheusQueryClient.queryScalar(promQl);
        return value.orElse(Double.NaN);
    }

    private MetricsIngestionProperties.MonitoredService findService(String serviceName) {
        for (MetricsIngestionProperties.MonitoredService service : properties.services()) {
            if (service.serviceName().equals(serviceName)) {
                return service;
            }
        }
        return null;
    }
}
