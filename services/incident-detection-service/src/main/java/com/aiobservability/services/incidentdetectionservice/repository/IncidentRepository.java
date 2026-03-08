package com.aiobservability.services.incidentdetectionservice.repository;

import com.aiobservability.services.incidentdetectionservice.model.IncidentRecord;
import com.aiobservability.services.incidentdetectionservice.model.IncidentSignalRecord;
import com.aiobservability.shared.models.IncidentStatus;
import com.aiobservability.shared.models.Severity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class IncidentRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final RowMapper<IncidentRecord> incidentMapper = (rs, ignored) -> new IncidentRecord(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("title"),
            rs.getString("description"),
            Severity.valueOf(rs.getString("severity")),
            IncidentStatus.valueOf(rs.getString("status")),
            fromJsonList(rs.getString("affected_services")),
            rs.getString("dominant_signal_type"),
            rs.getString("dominant_signal_key"),
            rs.getString("root_trace_id"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toInstant()
    );

    private final RowMapper<IncidentSignalRecord> signalMapper = (rs, ignored) -> new IncidentSignalRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("incident_id", UUID.class),
            rs.getString("signal_type"),
            rs.getString("signal_key"),
            fromJsonMap(rs.getString("signal_payload")),
            rs.getTimestamp("observed_at").toInstant()
    );

    public IncidentRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public IncidentRecord insertIncident(IncidentRecord incident) {
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into incidents (
                      id, code, title, description, severity, status, affected_services,
                      dominant_signal_type, dominant_signal_key, root_trace_id, created_at, updated_at, resolved_at
                    ) values (
                      ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?
                    )
                    """);
            statement.setObject(1, incident.id());
            statement.setString(2, incident.code());
            statement.setString(3, incident.title());
            statement.setString(4, incident.description());
            statement.setString(5, incident.severity().name());
            statement.setString(6, incident.status().name());
            statement.setString(7, toJson(incident.affectedServices()));
            statement.setString(8, incident.dominantSignalType());
            statement.setString(9, incident.dominantSignalKey());
            statement.setString(10, incident.rootTraceId());
            statement.setTimestamp(11, Timestamp.from(incident.createdAt()));
            statement.setTimestamp(12, Timestamp.from(incident.updatedAt()));
            if (incident.resolvedAt() == null) {
                statement.setTimestamp(13, null);
            } else {
                statement.setTimestamp(13, Timestamp.from(incident.resolvedAt()));
            }
            return statement;
        });
        return incident;
    }

    public void updateIncident(IncidentRecord incident) {
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    update incidents
                    set title = ?, description = ?, severity = ?, status = ?, affected_services = ?::jsonb,
                        dominant_signal_type = ?, dominant_signal_key = ?, root_trace_id = ?,
                        updated_at = ?, resolved_at = ?
                    where id = ?
                    """);
            statement.setString(1, incident.title());
            statement.setString(2, incident.description());
            statement.setString(3, incident.severity().name());
            statement.setString(4, incident.status().name());
            statement.setString(5, toJson(incident.affectedServices()));
            statement.setString(6, incident.dominantSignalType());
            statement.setString(7, incident.dominantSignalKey());
            statement.setString(8, incident.rootTraceId());
            statement.setTimestamp(9, Timestamp.from(incident.updatedAt()));
            if (incident.resolvedAt() == null) {
                statement.setTimestamp(10, null);
            } else {
                statement.setTimestamp(10, Timestamp.from(incident.resolvedAt()));
            }
            statement.setObject(11, incident.id());
            return statement;
        });
    }

    public IncidentRecord findById(UUID incidentId) {
        List<IncidentRecord> incidents = jdbcTemplate.query(
                "select * from incidents where id = ?",
                incidentMapper,
                incidentId
        );
        return incidents.isEmpty() ? null : incidents.get(0);
    }

    public List<IncidentRecord> findAll(int limit) {
        return jdbcTemplate.query(
                "select * from incidents order by created_at desc limit ?",
                incidentMapper,
                limit
        );
    }

    public IncidentRecord findOpenBySignalKey(String signalKey, Instant since) {
        List<IncidentRecord> incidents = jdbcTemplate.query(
                """
                select * from incidents
                where dominant_signal_key = ?
                  and status <> 'RESOLVED'
                  and updated_at >= ?
                order by updated_at desc
                limit 1
                """,
                incidentMapper,
                signalKey,
                Timestamp.from(since)
        );
        return incidents.isEmpty() ? null : incidents.get(0);
    }

    public void insertSignal(IncidentSignalRecord signal) {
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into incident_signals (
                      id, incident_id, signal_type, signal_key, signal_payload, observed_at
                    ) values (?, ?, ?, ?, ?::jsonb, ?)
                    """);
            statement.setObject(1, signal.id());
            statement.setObject(2, signal.incidentId());
            statement.setString(3, signal.signalType());
            statement.setString(4, signal.signalKey());
            statement.setString(5, toJson(signal.signalPayload()));
            statement.setTimestamp(6, Timestamp.from(signal.observedAt()));
            return statement;
        });
    }

    public List<IncidentSignalRecord> findSignals(UUID incidentId) {
        return jdbcTemplate.query(
                "select * from incident_signals where incident_id = ? order by observed_at asc",
                signalMapper,
                incidentId
        );
    }

    public List<String> findDistinctSignalTypes(UUID incidentId) {
        return jdbcTemplate.queryForList(
                "select distinct signal_type from incident_signals where incident_id = ?",
                String.class,
                incidentId
        );
    }

    public List<IncidentRecord> findOpenUpdatedAfter(Instant since) {
        return jdbcTemplate.query(
                """
                select * from incidents
                where status <> 'RESOLVED'
                  and updated_at >= ?
                order by updated_at desc
                """,
                incidentMapper,
                Timestamp.from(since)
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize JSON value", ex);
        }
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse affected_services json", ex);
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
            throw new IllegalStateException("Failed to parse signal_payload json", ex);
        }
    }
}
