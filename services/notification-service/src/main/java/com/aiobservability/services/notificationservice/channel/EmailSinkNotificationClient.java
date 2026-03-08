package com.aiobservability.services.notificationservice.channel;

import com.aiobservability.services.notificationservice.config.NotificationProperties;
import com.aiobservability.services.notificationservice.model.AlertMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailSinkNotificationClient implements NotificationChannelClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailSinkNotificationClient.class);
    private final NotificationProperties properties;

    public EmailSinkNotificationClient(NotificationProperties properties) {
        this.properties = properties;
    }

    @Override
    public String channel() {
        return "email-sink";
    }

    @Override
    public void send(AlertMessage message) {
        if (!properties.emailSinkEnabled()) {
            return;
        }
        LOGGER.info(
                "Email sink alert | incident={} severity={} title={} summary={}",
                message.incidentId(),
                message.severity(),
                message.title(),
                message.summary()
        );
    }
}
