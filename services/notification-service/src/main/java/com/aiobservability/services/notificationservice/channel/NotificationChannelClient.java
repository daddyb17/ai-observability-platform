package com.aiobservability.services.notificationservice.channel;

import com.aiobservability.services.notificationservice.model.AlertMessage;

public interface NotificationChannelClient {
    String channel();

    void send(AlertMessage message);
}
