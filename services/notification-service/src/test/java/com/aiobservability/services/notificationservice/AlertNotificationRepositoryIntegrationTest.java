package com.aiobservability.services.notificationservice;

import com.aiobservability.services.notificationservice.model.AlertNotificationRecord;
import com.aiobservability.services.notificationservice.model.AlertStatus;
import com.aiobservability.services.notificationservice.repository.AlertNotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AlertNotificationRepositoryIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("aiobs")
                    .withUsername("aiobs")
                    .withPassword("aiobs");

    private static JdbcTemplate jdbcTemplate;
    private static AlertNotificationRepository repository;

    @BeforeAll
    static void setup() {
        POSTGRES.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new AlertNotificationRepository(jdbcTemplate, new ObjectMapper());

        jdbcTemplate.execute("create table incidents (id uuid primary key)");
        jdbcTemplate.execute("""
                create table alert_notifications (
                    id uuid primary key,
                    incident_id uuid not null references incidents(id),
                    channel varchar(30) not null,
                    payload jsonb not null,
                    delivery_status varchar(20) not null,
                    attempt_count int not null default 0,
                    sent_at timestamptz,
                    error_message text
                )
                """);
    }

    @AfterAll
    static void cleanup() {
        POSTGRES.stop();
    }

    @Test
    void createAndUpdateLifecycle() {
        UUID incidentId = UUID.randomUUID();
        jdbcTemplate.update("insert into incidents(id) values (?)", incidentId);

        AlertNotificationRecord pending = repository.createPending(
                incidentId,
                "webhook",
                Map.of("title", "Test alert")
        );

        assertNotNull(pending.id());
        assertEquals(AlertStatus.PENDING, pending.deliveryStatus());
        assertEquals(0, pending.attemptCount());

        repository.markRetrying(pending.id(), 1, "temporary failure");
        repository.markSent(pending.id(), 2);

        AlertNotificationRecord stored = repository.findByIncidentId(incidentId, 10).getFirst();
        assertEquals(AlertStatus.SENT, stored.deliveryStatus());
        assertEquals(2, stored.attemptCount());
        assertNotNull(stored.sentAt());
    }

    @Test
    void markFailedPersistsError() {
        UUID incidentId = UUID.randomUUID();
        jdbcTemplate.update("insert into incidents(id) values (?)", incidentId);

        AlertNotificationRecord pending = repository.createPending(
                incidentId,
                "mock-slack",
                Map.of("title", "Will fail")
        );

        repository.markFailed(pending.id(), 3, "simulated permanent failure");

        AlertNotificationRecord stored = repository.findByIncidentId(incidentId, 10).getFirst();
        assertEquals(AlertStatus.FAILED, stored.deliveryStatus());
        assertEquals(3, stored.attemptCount());
        assertEquals("simulated permanent failure", stored.errorMessage());
    }
}
