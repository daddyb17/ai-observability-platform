package com.aiobservability.services.incidentdetectionservice;

import com.aiobservability.services.incidentdetectionservice.config.IncidentProperties;
import com.aiobservability.services.incidentdetectionservice.kafka.IncidentEventPublisher;
import com.aiobservability.services.incidentdetectionservice.model.IncidentRecord;
import com.aiobservability.services.incidentdetectionservice.model.IncidentSignalRecord;
import com.aiobservability.services.incidentdetectionservice.repository.IncidentRepository;
import com.aiobservability.services.incidentdetectionservice.service.IncidentRuleEngine;
import com.aiobservability.services.incidentdetectionservice.service.IncidentRuleConfigService;
import com.aiobservability.services.incidentdetectionservice.service.IncidentService;
import com.aiobservability.services.incidentdetectionservice.service.SeverityCalculator;
import com.aiobservability.shared.models.IncidentStatus;
import com.aiobservability.shared.models.LogEventPayload;
import com.aiobservability.shared.models.Severity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Testcontainers(disabledWithoutDocker = true)
class IncidentRuleEngineIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("aiobs")
                    .withUsername("aiobs")
                    .withPassword("aiobs");

    private static IncidentRepository incidentRepository;
    private static IncidentRuleEngine incidentRuleEngine;
    private static IncidentEventPublisher eventPublisher;

    @BeforeAll
    static void setup() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                create table incidents (
                    id uuid primary key,
                    code varchar(50) unique not null,
                    title varchar(255) not null,
                    description text,
                    severity varchar(20) not null,
                    status varchar(30) not null,
                    affected_services jsonb not null,
                    dominant_signal_type varchar(50),
                    dominant_signal_key varchar(255),
                    root_trace_id varchar(255),
                    created_at timestamptz not null,
                    updated_at timestamptz not null,
                    resolved_at timestamptz
                )
                """);
        jdbcTemplate.execute("""
                create table incident_signals (
                    id uuid primary key,
                    incident_id uuid not null references incidents(id),
                    signal_type varchar(50) not null,
                    signal_key varchar(255) not null,
                    signal_payload jsonb not null,
                    observed_at timestamptz not null
                )
                """);

        incidentRepository = new IncidentRepository(jdbcTemplate, new ObjectMapper());
        eventPublisher = mock(IncidentEventPublisher.class);

        IncidentProperties properties = new IncidentProperties(
                new IncidentProperties.RuleProperties(10, 20, 5, 3, 5),
                new IncidentProperties.KafkaProperties(
                        "incidents.detected",
                        "incidents.updated",
                        "ai.analysis.request",
                        "alerts.outbound",
                        5
                )
        );
        IncidentRuleConfigService ruleConfigService = new IncidentRuleConfigService(properties);
        IncidentService incidentService = new IncidentService(
                incidentRepository,
                new SeverityCalculator(),
                eventPublisher,
                ruleConfigService
        );
        incidentRuleEngine = new IncidentRuleEngine(ruleConfigService, incidentService);
    }

    @AfterAll
    static void cleanup() {
        POSTGRES.stop();
    }

    @Test
    void createsIncidentAfterRepeatedExceptionBurst() {
        Instant baseTime = Instant.parse("2026-03-08T10:15:30Z");
        for (int i = 0; i < 20; i++) {
            LogEventPayload event = new LogEventPayload(
                    "log-" + i,
                    "payment-service",
                    "dev",
                    baseTime.plusSeconds(i * 5L),
                    "ERROR",
                    "Database connection timeout",
                    "SQLTimeoutException",
                    "stack",
                    "trace-abc-123",
                    "span-" + i,
                    "payment-service-1",
                    Map.of("region", "local"),
                    baseTime.plusSeconds(i * 5L)
            );
            incidentRuleEngine.onLog(event);
        }

        var incidents = incidentRepository.findAll(10);
        assertEquals(1, incidents.size());

        IncidentRecord incident = incidents.get(0);
        assertEquals(IncidentStatus.OPEN, incident.status());
        assertEquals(Severity.LOW, incident.severity());
        assertEquals("LOG_EXCEPTION_BURST", incident.dominantSignalType());
        assertEquals("payment-service:SQLTimeoutException", incident.dominantSignalKey());
        assertTrue(incident.code().startsWith("INC-"));

        var signals = incidentRepository.findSignals(incident.id());
        assertEquals(1, signals.size());
        IncidentSignalRecord signal = signals.get(0);
        assertEquals("LOG_EXCEPTION_BURST", signal.signalType());
        assertEquals("payment-service:SQLTimeoutException", signal.signalKey());

        verify(eventPublisher, atLeastOnce()).publishDetected(any(IncidentRecord.class));
        verify(eventPublisher, atLeastOnce()).publishAnalysisRequest(any(String.class));
        verify(eventPublisher, atLeastOnce()).publishAlertOutbound(any(IncidentRecord.class));
    }
}
