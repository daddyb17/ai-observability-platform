package com.aiobservability.services.logingestionservice.model;

public record LogIngestionResult(
        boolean accepted,
        String eventId,
        String error
) {
    public static LogIngestionResult success(String eventId) {
        return new LogIngestionResult(true, eventId, null);
    }

    public static LogIngestionResult failed(String error) {
        return new LogIngestionResult(false, null, error);
    }
}
