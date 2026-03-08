package com.aiobservability.services.notificationservice.repository;

import com.aiobservability.services.notificationservice.model.AlertNotificationRecord;
import com.aiobservability.services.notificationservice.model.AlertStatus;
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
public class AlertNotificationRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final RowMapper<AlertNotificationRecord> mapper = (rs, ignored) -> new AlertNotificationRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("incident_id", UUID.class),
            rs.getString("channel"),
            fromJsonMap(rs.getString("payload")),
            AlertStatus.valueOf(rs.getString("delivery_status")),
            rs.getInt("attempt_count"),
            rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant(),
            rs.getString("error_message")
    );

    public AlertNotificationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public AlertNotificationRecord createPending(UUID incidentId, String channel, Map<String, Object> payload) {
        AlertNotificationRecord record = new AlertNotificationRecord(
                UUID.randomUUID(),
                incidentId,
                channel,
                payload,
                AlertStatus.PENDING,
                0,
                null,
                null
        );
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into alert_notifications (
                        id, incident_id, channel, payload, delivery_status, attempt_count, sent_at, error_message
                    ) values (?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                    """);
            statement.setObject(1, record.id());
            statement.setObject(2, record.incidentId());
            statement.setString(3, record.channel());
            statement.setString(4, toJson(record.payload()));
            statement.setString(5, record.deliveryStatus().name());
            statement.setInt(6, record.attemptCount());
            statement.setTimestamp(7, null);
            statement.setString(8, null);
            return statement;
        });
        return record;
    }

    public void markRetrying(UUID notificationId, int attemptCount, String errorMessage) {
        updateStatus(notificationId, AlertStatus.RETRYING, attemptCount, null, errorMessage);
    }

    public void markSent(UUID notificationId, int attemptCount) {
        updateStatus(notificationId, AlertStatus.SENT, attemptCount, Instant.now(), null);
    }

    public void markFailed(UUID notificationId, int attemptCount, String errorMessage) {
        updateStatus(notificationId, AlertStatus.FAILED, attemptCount, null, errorMessage);
    }

    public List<AlertNotificationRecord> findLatest(int limit) {
        return jdbcTemplate.query(
                "select * from alert_notifications order by coalesce(sent_at, now()) desc, attempt_count desc limit ?",
                mapper,
                limit
        );
    }

    public List<AlertNotificationRecord> findByIncidentId(UUID incidentId, int limit) {
        return jdbcTemplate.query(
                """
                select * from alert_notifications
                where incident_id = ?
                order by coalesce(sent_at, now()) desc, attempt_count desc
                limit ?
                """,
                mapper,
                incidentId,
                limit
        );
    }

    private void updateStatus(
            UUID notificationId,
            AlertStatus status,
            int attemptCount,
            Instant sentAt,
            String errorMessage
    ) {
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    update alert_notifications
                    set delivery_status = ?, attempt_count = ?, sent_at = ?, error_message = ?
                    where id = ?
                    """);
            statement.setString(1, status.name());
            statement.setInt(2, attemptCount);
            if (sentAt == null) {
                statement.setTimestamp(3, null);
            } else {
                statement.setTimestamp(3, Timestamp.from(sentAt));
            }
            statement.setString(4, errorMessage);
            statement.setObject(5, notificationId);
            return statement;
        });
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize JSON", ex);
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
            throw new IllegalStateException("Failed to parse JSON", ex);
        }
    }
}
