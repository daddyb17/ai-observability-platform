package com.aiobservability.services.incidentdetectionservice;

import com.aiobservability.services.incidentdetectionservice.config.IncidentProperties;
import com.aiobservability.services.incidentdetectionservice.kafka.IncidentEventPublisher;
import com.aiobservability.services.incidentdetectionservice.model.IncidentRecord;
import com.aiobservability.services.incidentdetectionservice.model.IncidentSignalRecord;
import com.aiobservability.services.incidentdetectionservice.model.IncidentTimelineEntry;
import com.aiobservability.services.incidentdetectionservice.repository.IncidentRepository;
import com.aiobservability.services.incidentdetectionservice.service.IncidentRuleConfigService;
import com.aiobservability.services.incidentdetectionservice.service.IncidentService;
import com.aiobservability.services.incidentdetectionservice.service.SeverityCalculator;
import com.aiobservability.shared.models.IncidentStatus;
import com.aiobservability.shared.models.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncidentServiceTimelineTest {
    @Test
    void buildsChronologicalTimelineFromIncidentAndSignals() {
        IncidentRepository repository = mock(IncidentRepository.class);
        IncidentEventPublisher publisher = mock(IncidentEventPublisher.class);

        IncidentService service = new IncidentService(
                repository,
                new SeverityCalculator(),
                publisher,
                new IncidentRuleConfigService(
                        new IncidentProperties(
                                new IncidentProperties.RuleProperties(10, 20, 5, 3, 5),
                                new IncidentProperties.KafkaProperties(
                                        "incidents.detected",
                                        "incidents.updated",
                                        "ai.analysis.request",
                                        "alerts.outbound",
                                        5
                                )
                        )
                )
        );

        UUID incidentId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-03-08T10:00:00Z");
        Instant signalAt = Instant.parse("2026-03-08T10:01:00Z");
        Instant updatedAt = Instant.parse("2026-03-08T10:02:00Z");
        Instant resolvedAt = Instant.parse("2026-03-08T10:03:00Z");

        IncidentRecord incident = new IncidentRecord(
                incidentId,
                "INC-1001",
                "Payment timeout burst",
                "Repeated SQL timeouts",
                Severity.HIGH,
                IncidentStatus.RESOLVED,
                List.of("payment-service"),
                "LOG_EXCEPTION_BURST",
                "payment-service:SQLTimeoutException",
                "trace-abc-123",
                createdAt,
                updatedAt,
                resolvedAt
        );
        IncidentSignalRecord signal = new IncidentSignalRecord(
                UUID.randomUUID(),
                incidentId,
                "LOG_EXCEPTION_BURST",
                "payment-service:SQLTimeoutException",
                Map.of("exceptionType", "SQLTimeoutException"),
                signalAt
        );

        when(repository.findById(incidentId)).thenReturn(incident);
        when(repository.findSignals(incidentId)).thenReturn(List.of(signal));

        List<IncidentTimelineEntry> timeline = service.getIncidentTimeline(incidentId);

        assertEquals(4, timeline.size());
        assertEquals("INCIDENT_CREATED", timeline.get(0).eventType());
        assertEquals("SIGNAL_OBSERVED", timeline.get(1).eventType());
        assertEquals("INCIDENT_UPDATED", timeline.get(2).eventType());
        assertEquals("INCIDENT_RESOLVED", timeline.get(3).eventType());
        assertTrue(timeline.get(0).timestamp().isBefore(timeline.get(3).timestamp()));
    }
}
