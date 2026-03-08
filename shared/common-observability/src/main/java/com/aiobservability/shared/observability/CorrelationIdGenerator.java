package com.aiobservability.shared.observability;

import java.util.UUID;

public final class CorrelationIdGenerator {
    private CorrelationIdGenerator() {
    }

    public static String nextId() {
        return UUID.randomUUID().toString();
    }
}
