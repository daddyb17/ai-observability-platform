package com.aiobservability.services.notificationservice.model;

public record DlqReplayRequest(
        String originalTopic,
        String originalPayload,
        String key,
        String dlqMessage
) {
}
