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
public class WebhookNotificationClient implements NotificationChannelClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookNotificationClient.class);
    private final NotificationProperties properties;

    public WebhookNotificationClient(NotificationProperties properties) {
        this.properties = properties;
    }

    @Override
    public String channel() {
        return "webhook";
    }

    @Override
    public void send(AlertMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("incidentId", message.incidentId().toString());
        payload.put("severity", message.severity());
        payload.put("title", message.title());
        payload.put("summary", message.summary());
        payload.put("recommendedActions", message.recommendedActions());
        payload.put("sourceEventType", message.sourceEventType());

        String url = properties.webhookUrl();
        if (url == null || url.isBlank()) {
            LOGGER.info("Webhook URL not configured; simulated webhook send: {}", payload);
            return;
        }

        RestClient.create().post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
