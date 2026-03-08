package com.aiobservability.services.aianalysisservice.repository;

import com.aiobservability.services.aianalysisservice.model.IncidentContext;
import com.aiobservability.services.aianalysisservice.model.IncidentSignalRecord;
import com.aiobservability.services.aianalysisservice.model.TraceSummaryRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class IncidentContextRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final RowMapper<IncidentContext> incidentMapper = (rs, ignored) -> new IncidentContext(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("severity"),
            rs.getString("status"),
            fromJsonList(rs.getString("affected_services")),
            rs.getString("root_trace_id"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private final RowMapper<IncidentSignalRecord> signalMapper = (rs, ignored) -> new IncidentSignalRecord(
            rs.getString("signal_type"),
            rs.getString("signal_key"),
            fromJsonMap(rs.getString("signal_payload")),
            rs.getTimestamp("observed_at").toInstant()
    );

    private final RowMapper<TraceSummaryRecord> traceSummaryMapper = (rs, ignored) -> new TraceSummaryRecord(
            rs.getString("trace_id"),
            rs.getString("root_service"),
            rs.getLong("duration_ms"),
            rs.getBoolean("error_flag"),
            rs.getInt("span_count"),
            rs.getString("bottleneck_service"),
            rs.getString("bottleneck_span"),
            rs.getTimestamp("started_at").toInstant()
    );

    public IncidentContextRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public IncidentContext findIncident(UUID incidentId) {
        List<IncidentContext> results = jdbcTemplate.query(
                "select * from incidents where id = ?",
                incidentMapper,
                incidentId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    public List<IncidentSignalRecord> findSignals(UUID incidentId, int limit) {
        return jdbcTemplate.query(
                """
                select signal_type, signal_key, signal_payload, observed_at
                from incident_signals
                where incident_id = ?
                order by observed_at desc
                limit ?
                """,
                signalMapper,
                incidentId,
                limit
        );
    }

    public TraceSummaryRecord findTraceSummary(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        List<TraceSummaryRecord> results = jdbcTemplate.query(
                "select * from trace_summaries where trace_id = ?",
                traceSummaryMapper,
                traceId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse affected_services JSON", ex);
        }
    }

    private Map<String, Object> fromJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse signal_payload JSON", ex);
        }
    }
}
