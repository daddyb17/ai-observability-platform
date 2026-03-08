package com.aiobservability.services.notificationservice.channel;

import com.aiobservability.services.notificationservice.config.NotificationProperties;
import com.aiobservability.services.notificationservice.model.AlertMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MockSlackNotificationClient implements NotificationChannelClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MockSlackNotificationClient.class);
    private final NotificationProperties properties;

    public MockSlackNotificationClient(NotificationProperties properties) {
        this.properties = properties;
    }

    @Override
    public String channel() {
        return "mock-slack";
    }

    @Override
    public void send(AlertMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", "[" + message.severity() + "] " + message.title() + " - " + message.summary());
        payload.put("incidentId", message.incidentId().toString());
        payload.put("recommendedActions", message.recommendedActions());

        String webhookUrl = properties.slackWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            LOGGER.info("Mock Slack delivery: {}", payload);
            return;
        }

        RestClient.create().post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
