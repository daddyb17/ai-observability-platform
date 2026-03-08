package com.aiobservability.services.metricsingestionservice.service;

import com.aiobservability.services.metricsingestionservice.config.MetricsIngestionProperties;
import com.aiobservability.shared.models.MetricSignalEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class MetricSignalStore {
    private static final int MAX_SIGNALS_PER_SERVICE = 500;

    private final MetricsIngestionProperties properties;
    private final Map<String, ConcurrentLinkedDeque<MetricSignalEvent>> byService = new ConcurrentHashMap<>();

    public MetricSignalStore(MetricsIngestionProperties properties) {
        this.properties = properties;
    }

    public void add(MetricSignalEvent signal) {
        ConcurrentLinkedDeque<MetricSignalEvent> queue = byService.computeIfAbsent(
                signal.serviceName(),
                ignored -> new ConcurrentLinkedDeque<>()
        );
        queue.addFirst(signal);
        while (queue.size() > MAX_SIGNALS_PER_SERVICE) {
            queue.pollLast();
        }
    }

    public List<MetricSignalEvent> getByService(String serviceName) {
        List<MetricSignalEvent> signals = new ArrayList<>(byService.getOrDefault(serviceName, new ConcurrentLinkedDeque<>()));
        signals.sort(Comparator.comparing(MetricSignalEvent::timestamp).reversed());
        return signals;
    }

    public List<MetricSignalEvent> getForIncident(String incidentId) {
        int limit = properties.defaultIncidentSignalsSize();
        List<MetricSignalEvent> all = new ArrayList<>();
        byService.values().forEach(all::addAll);
        all.sort(Comparator.comparing(MetricSignalEvent::timestamp).reversed());
        if (all.size() <= limit) {
            return all;
        }
        return all.subList(0, limit);
    }
}
