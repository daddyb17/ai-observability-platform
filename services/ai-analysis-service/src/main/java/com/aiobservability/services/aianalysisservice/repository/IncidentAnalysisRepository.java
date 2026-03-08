package com.aiobservability.services.aianalysisservice.repository;

import com.aiobservability.services.aianalysisservice.model.AiAnalysisRecord;
import com.aiobservability.services.aianalysisservice.model.AiAnalysisResult;
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
public class IncidentAnalysisRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final RowMapper<AiAnalysisRecord> analysisMapper = (rs, ignored) -> new AiAnalysisRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("incident_id", UUID.class),
            rs.getString("provider"),
            rs.getString("model_name"),
            rs.getString("summary"),
            rs.getString("root_cause"),
            rs.getBigDecimal("confidence").doubleValue(),
            fromJsonList(rs.getString("recommended_actions")),
            fromJsonList(rs.getString("evidence")),
            fromJsonMap(rs.getString("raw_response")),
            rs.getTimestamp("created_at").toInstant()
    );

    public IncidentAnalysisRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public AiAnalysisRecord save(UUID incidentId, AiAnalysisResult result) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into incident_analysis (
                      id, incident_id, provider, model_name, summary, root_cause, confidence,
                      recommended_actions, evidence, raw_response, created_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?)
                    """);
            statement.setObject(1, id);
            statement.setObject(2, incidentId);
            statement.setString(3, result.provider());
            statement.setString(4, result.modelName());
            statement.setString(5, result.summary());
            statement.setString(6, result.rootCause());
            statement.setDouble(7, result.confidence());
            statement.setString(8, toJson(result.recommendedActions()));
            statement.setString(9, toJson(result.evidence()));
            statement.setString(10, toJson(result.rawResponse()));
            statement.setTimestamp(11, Timestamp.from(now));
            return statement;
        });

        return new AiAnalysisRecord(
                id,
                incidentId,
                result.provider(),
                result.modelName(),
                result.summary(),
                result.rootCause(),
                result.confidence(),
                result.recommendedActions(),
                result.evidence(),
                result.rawResponse(),
                now
        );
    }

    public AiAnalysisRecord findLatestByIncident(UUID incidentId) {
        List<AiAnalysisRecord> results = jdbcTemplate.query(
                """
                select * from incident_analysis
                where incident_id = ?
                order by created_at desc
                limit 1
                """,
                analysisMapper,
                incidentId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize JSON payload", ex);
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
            throw new IllegalStateException("Failed to parse JSON array", ex);
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
            throw new IllegalStateException("Failed to parse JSON object", ex);
        }
    }
}
