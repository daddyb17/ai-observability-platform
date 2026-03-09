package com.aiobservability.services.aianalysisservice;

import com.aiobservability.services.aianalysisservice.client.AiClientResolver;
import com.aiobservability.services.aianalysisservice.client.MockAiClient;
import com.aiobservability.services.aianalysisservice.config.AiProperties;
import com.aiobservability.services.aianalysisservice.consumer.AiAnalysisRequestConsumer;
import com.aiobservability.services.aianalysisservice.kafka.AiAnalysisResultPublisher;
import com.aiobservability.services.aianalysisservice.model.AiAnalysisRecord;
import com.aiobservability.services.aianalysisservice.repository.IncidentAnalysisRepository;
import com.aiobservability.services.aianalysisservice.repository.IncidentContextRepository;
import com.aiobservability.services.aianalysisservice.service.AiAnalysisService;
import com.aiobservability.services.aianalysisservice.service.IncidentEvidenceAssembler;
import com.aiobservability.services.aianalysisservice.service.PromptBuilder;
import com.aiobservability.services.aianalysisservice.service.RedactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Testcontainers(disabledWithoutDocker = true)
class AiAnalysisRequestConsumerIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("aiobs")
                    .withUsername("aiobs")
                    .withPassword("aiobs");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static JdbcTemplate jdbcTemplate;
    private static IncidentAnalysisRepository analysisRepository;
    private static AiAnalysisResultPublisher resultPublisher;
    private static AiAnalysisRequestConsumer consumer;

    @BeforeAll
    static void setup() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();

        IncidentContextRepository contextRepository = new IncidentContextRepository(jdbcTemplate, OBJECT_MAPPER);
        analysisRepository = new IncidentAnalysisRepository(jdbcTemplate, OBJECT_MAPPER);
        IncidentEvidenceAssembler evidenceAssembler = new IncidentEvidenceAssembler(contextRepository, new RedactionService());
        PromptBuilder promptBuilder = new PromptBuilder();

        AiProperties properties = new AiProperties(
                "mock",
                10,
                1,
                new AiProperties.KafkaProperties(
                        "ai.analysis.request",
                        "ai.analysis.result",
                        "deadletter.ai",
                        5,
                        "ai-analysis-service"
                ),
                new AiProperties.OpenAiProperties("gpt-4o-mini", "", "https://api.openai.com/v1"),
                new AiProperties.OllamaProperties("llama3", "http://localhost:11434")
        );

        resultPublisher = mock(AiAnalysisResultPublisher.class);
        AiAnalysisService analysisService = new AiAnalysisService(
                contextRepository,
                analysisRepository,
                evidenceAssembler,
                promptBuilder,
                new AiClientResolver(List.of(new MockAiClient()), properties),
                resultPublisher,
                properties
        );
        consumer = new AiAnalysisRequestConsumer(OBJECT_MAPPER, analysisService, properties);
    }

    @AfterAll
    static void cleanup() {
        POSTGRES.stop();
    }

    @Test
    void persistsAnalysisAfterAnalysisRequestEvent() throws Exception {
        UUID incidentId = UUID.randomUUID();
        seedIncidentContext(incidentId);

        String message = OBJECT_MAPPER.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "AI_ANALYSIS_REQUESTED",
                "eventVersion", "1.0",
                "occurredAt", Instant.now().toString(),
                "source", "incident-detection-service",
                "payload", Map.of(
                        "incidentId", incidentId.toString(),
                        "requestedAt", Instant.now().toString()
                )
        ));

        consumer.consume(message);

        AiAnalysisRecord stored = analysisRepository.findLatestByIncident(incidentId);
        assertNotNull(stored);
        assertEquals(incidentId, stored.incidentId());
        assertEquals("mock", stored.provider());
        assertFalse(stored.summary().isBlank());
        assertFalse(stored.rootCause().isBlank());
        assertFalse(stored.recommendedActions().isEmpty());
        assertTrue(stored.confidence() > 0.0);

        verify(resultPublisher).publish(any(AiAnalysisRecord.class));
    }

    private static void createSchema() {
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
        jdbcTemplate.execute("""
                create table trace_summaries (
                    trace_id varchar(255) primary key,
                    root_service varchar(100) not null,
                    duration_ms bigint not null,
                    error_flag boolean not null,
                    span_count int not null,
                    bottleneck_service varchar(100),
                    bottleneck_span varchar(255),
                    started_at timestamptz not null
                )
                """);
        jdbcTemplate.execute("""
                create table incident_analysis (
                    id uuid primary key,
                    incident_id uuid not null references incidents(id),
                    provider varchar(50) not null,
                    model_name varchar(100) not null,
                    summary text,
                    root_cause text,
                    confidence numeric(4,3),
                    recommended_actions jsonb,
                    evidence jsonb,
                    raw_response jsonb,
                    created_at timestamptz not null
                )
                """);
    }

    private static void seedIncidentContext(UUID incidentId) throws Exception {
        Instant now = Instant.parse("2026-03-08T10:16:10Z");
        String traceId = "trace-abc-123";

        jdbcTemplate.update(
                """
                insert into incidents (
                    id, code, title, description, severity, status, affected_services,
                    dominant_signal_type, dominant_signal_key, root_trace_id, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                """,
                incidentId,
                "INC-1001",
                "Payment service timeout burst",
                "Repeated SQL timeout errors",
                "HIGH",
                "OPEN",
                OBJECT_MAPPER.writeValueAsString(List.of("payment-service")),
                "LOG_EXCEPTION_BURST",
                "payment-service:SQLTimeoutException",
                traceId,
                Timestamp.from(now),
                Timestamp.from(now)
        );

        jdbcTemplate.update(
                """
                insert into incident_signals (
                    id, incident_id, signal_type, signal_key, signal_payload, observed_at
                ) values (?, ?, ?, ?, ?::jsonb, ?)
                """,
                UUID.randomUUID(),
                incidentId,
                "LOG_EXCEPTION_BURST",
                "payment-service:SQLTimeoutException",
                OBJECT_MAPPER.writeValueAsString(Map.of(
                        "message", "Database timeout token=abc123",
                        "exceptionType", "SQLTimeoutException"
                )),
                Timestamp.from(now)
        );

        jdbcTemplate.update(
                """
                insert into incident_signals (
                    id, incident_id, signal_type, signal_key, signal_payload, observed_at
                ) values (?, ?, ?, ?, ?::jsonb, ?)
                """,
                UUID.randomUUID(),
                incidentId,
                "METRIC_ERROR_RATE_BREACH",
                "payment-service:http_server_requests_error_rate",
                OBJECT_MAPPER.writeValueAsString(Map.of(
                        "metricName", "http_server_requests_error_rate",
                        "value", 0.28,
                        "threshold", 0.10,
                        "window", "5m"
                )),
                Timestamp.from(now.plusSeconds(10))
        );

        jdbcTemplate.update(
                """
                insert into trace_summaries (
                    trace_id, root_service, duration_ms, error_flag, span_count,
                    bottleneck_service, bottleneck_span, started_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                traceId,
                "order-service",
                5210L,
                true,
                12,
                "payment-service",
                "chargeCard",
                Timestamp.from(now.minusSeconds(5))
        );
    }
}
