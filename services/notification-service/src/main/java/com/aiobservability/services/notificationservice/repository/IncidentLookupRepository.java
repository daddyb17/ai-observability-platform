package com.aiobservability.services.notificationservice.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class IncidentLookupRepository {
    private final JdbcTemplate jdbcTemplate;

    public IncidentLookupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID findLatestIncidentId() {
        List<UUID> ids = jdbcTemplate.queryForList(
                "select id from incidents order by created_at desc limit 1",
                UUID.class
        );
        return ids.isEmpty() ? null : ids.get(0);
    }
}
