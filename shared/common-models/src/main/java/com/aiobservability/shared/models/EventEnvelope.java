package com.aiobservability.shared.models;

import java.time.Instant;

public record EventEnvelope<T>(
        String eventId,
        String eventType,
        String eventVersion,
        Instant occurredAt,
        String source,
        T payload
) {
}
