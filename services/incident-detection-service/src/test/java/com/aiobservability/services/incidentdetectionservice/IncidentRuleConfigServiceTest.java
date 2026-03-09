package com.aiobservability.services.incidentdetectionservice;

import com.aiobservability.services.incidentdetectionservice.config.IncidentProperties;
import com.aiobservability.services.incidentdetectionservice.model.IncidentRuleConfig;
import com.aiobservability.services.incidentdetectionservice.model.IncidentRulesUpdateRequest;
import com.aiobservability.services.incidentdetectionservice.service.IncidentRuleConfigService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentRuleConfigServiceTest {
    @Test
    void updatesOnlyProvidedRuleFields() {
        IncidentRuleConfigService service = new IncidentRuleConfigService(defaultProperties());
        IncidentRuleConfig updated = service.update(new IncidentRulesUpdateRequest(
                null,
                30,
                null,
                null,
                7
        ));

        assertEquals(10, updated.correlationWindowMinutes());
        assertEquals(30, updated.errorBurstThreshold());
        assertEquals(5, updated.errorBurstWindowMinutes());
        assertEquals(3, updated.traceFailureThreshold());
        assertEquals(7, updated.traceFailureWindowMinutes());
    }

    @Test
    void rejectsNonPositiveThresholdUpdates() {
        IncidentRuleConfigService service = new IncidentRuleConfigService(defaultProperties());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.update(new IncidentRulesUpdateRequest(0, null, null, null, null))
        );
    }

    private IncidentProperties defaultProperties() {
        return new IncidentProperties(
                new IncidentProperties.RuleProperties(10, 20, 5, 3, 5),
                new IncidentProperties.KafkaProperties(
                        "incidents.detected",
                        "incidents.updated",
                        "ai.analysis.request",
                        "alerts.outbound",
                        5
                )
        );
    }
}
