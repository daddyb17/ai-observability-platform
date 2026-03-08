package com.aiobservability.sampleapps.orderservice.model;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderRecord(
        String orderId,
        BigDecimal amount,
        String currency,
        String status,
        String paymentId,
        String notificationId,
        Instant createdAt
) {
}
