package com.aiobservability.services.incidentdetectionservice.service;

import com.aiobservability.services.incidentdetectionservice.config.IncidentProperties;
import com.aiobservability.services.incidentdetectionservice.kafka.IncidentEventPublisher;
import com.aiobservability.services.incidentdetectionservice.model.IncidentCandidate;
import com.aiobservability.services.incidentdetectionservice.model.IncidentRecord;
import com.aiobservability.services.incidentdetectionservice.model.IncidentSignalRecord;
import com.aiobservability.services.incidentdetectionservice.repository.IncidentRepository;
import com.aiobservability.shared.models.IncidentStatus;
import com.aiobservability.shared.models.Severity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class IncidentService {
    private final IncidentRepository incidentRepository;
    private final SeverityCalculator severityCalculator;
    private final IncidentEventPublisher incidentEventPublisher;
    private final IncidentProperties properties;
    private final AtomicInteger incidentSequence = new AtomicInteger(1000);

    public IncidentService(
            IncidentRepository incidentRepository,
            SeverityCalculator severityCalculator,
            IncidentEventPublisher incidentEventPublisher,
            IncidentProperties properties
    ) {
        this.incidentRepository = incidentRepository;
        this.severityCalculator = severityCalculator;
        this.incidentEventPublisher = incidentEventPublisher;
        this.properties = properties;
    }

    public synchronized IncidentRecord processCandidate(IncidentCandidate candidate) {
        Instant now = Instant.now();
        Instant correlationThreshold = now.minus(properties.rules().correlationWindowMinutes(), ChronoUnit.MINUTES);
        IncidentRecord existing = incidentRepository.findOpenBySignalKey(candidate.signalKey(), correlationThreshold);

        if (existing == null) {
            IncidentRecord created = createIncident(candidate, now);
            incidentRepository.insertIncident(created);
            insertSignal(created.id(), candidate);
            incidentEventPublisher.publishDetected(created);
            incidentEventPublisher.publishAnalysisRequest(created.id().toString());
            incidentEventPublisher.publishAlertOutbound(created);
            return created;
        }

        insertSignal(existing.id(), candidate);
        List<String> signalTypes = incidentRepository.findDistinctSignalTypes(existing.id());
        Severity recalculated = severityCalculator.calculate(signalTypes);
        Severity previousSeverity = existing.severity();
        Set<String> mergedServices = new LinkedHashSet<>(existing.affectedServices());
        mergedServices.addAll(candidate.affectedServices());

        IncidentRecord updated = new IncidentRecord(
                existing.id(),
                existing.code(),
                existing.title(),
                existing.description(),
                recalculated,
                existing.status(),
                new ArrayList<>(mergedServices),
                existing.dominantSignalType(),
                existing.dominantSignalKey(),
                existing.rootTraceId() == null ? candidate.rootTraceId() : existing.rootTraceId(),
                existing.createdAt(),
                now,
                existing.resolvedAt()
        );
        incidentRepository.updateIncident(updated);
        incidentEventPublisher.publishUpdated(updated);

        if (recalculated.ordinal() > previousSeverity.ordinal() && recalculated.ordinal() >= Severity.HIGH.ordinal()) {
            incidentEventPublisher.publishAnalysisRequest(updated.id().toString());
            incidentEventPublisher.publishAlertOutbound(updated);
        }
        return updated;
    }

    public List<IncidentRecord> listIncidents() {
        return incidentRepository.findAll(200);
    }

    public IncidentRecord getIncident(UUID incidentId) {
        IncidentRecord incident = incidentRepository.findById(incidentId);
        if (incident == null) {
            throw new ResponseStatusException(NOT_FOUND, "Incident not found: " + incidentId);
        }
        return incident;
    }

    public List<IncidentSignalRecord> getIncidentSignals(UUID incidentId) {
        return incidentRepository.findSignals(incidentId);
    }

    public IncidentRecord updateStatus(UUID incidentId, IncidentStatus status) {
        IncidentRecord existing = getIncident(incidentId);
        Instant now = Instant.now();
        IncidentRecord updated = new IncidentRecord(
                existing.id(),
                existing.code(),
                existing.title(),
                existing.description(),
                existing.severity(),
                status,
                existing.affectedServices(),
                existing.dominantSignalType(),
                existing.dominantSignalKey(),
                existing.rootTraceId(),
                existing.createdAt(),
                now,
                status == IncidentStatus.RESOLVED ? now : null
        );
        incidentRepository.updateIncident(updated);
        incidentEventPublisher.publishUpdated(updated);
        if (status == IncidentStatus.RESOLVED) {
            incidentEventPublisher.publishAlertOutbound(updated);
        }
        return updated;
    }

    public void requestAnalysis(UUID incidentId) {
        getIncident(incidentId);
        incidentEventPublisher.publishAnalysisRequest(incidentId.toString());
    }

    public Map<String, Object> rulesSummary() {
        return Map.of(
                "errorBurst", Map.of(
                        "threshold", properties.rules().errorBurstThreshold(),
                        "windowMinutes", properties.rules().errorBurstWindowMinutes()
                ),
                "traceFailureCluster", Map.of(
                        "threshold", properties.rules().traceFailureThreshold(),
                        "windowMinutes", properties.rules().traceFailureWindowMinutes()
                ),
                "correlationWindowMinutes", properties.rules().correlationWindowMinutes()
        );
    }

    public Map<String, Object> evaluateSummary() {
        List<IncidentRecord> openIncidents = incidentRepository.findOpenUpdatedAfter(
                Instant.now().minus(properties.rules().correlationWindowMinutes(), ChronoUnit.MINUTES)
        );
        return Map.of(
                "evaluatedAt", Instant.now(),
                "openIncidents", openIncidents.size()
        );
    }

    private IncidentRecord createIncident(IncidentCandidate candidate, Instant now) {
        String code = "INC-" + incidentSequence.incrementAndGet();
        Severity severity = severityCalculator.calculate(List.of(candidate.signalType()));
        return new IncidentRecord(
                UUID.randomUUID(),
                code,
                candidate.title(),
                candidate.description(),
                severity,
                IncidentStatus.OPEN,
                candidate.affectedServices(),
                candidate.signalType(),
                candidate.signalKey(),
                candidate.rootTraceId(),
                now,
                now,
                null
        );
    }

    private void insertSignal(UUID incidentId, IncidentCandidate candidate) {
        IncidentSignalRecord signal = new IncidentSignalRecord(
                UUID.randomUUID(),
                incidentId,
                candidate.signalType(),
                candidate.signalKey(),
                new LinkedHashMap<>(candidate.payload()),
                candidate.observedAt()
        );
        incidentRepository.insertSignal(signal);
    }
}
