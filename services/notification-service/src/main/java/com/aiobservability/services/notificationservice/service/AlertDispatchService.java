package com.aiobservability.services.notificationservice.service;

import com.aiobservability.services.notificationservice.channel.NotificationChannelClient;
import com.aiobservability.services.notificationservice.config.NotificationProperties;
import com.aiobservability.services.notificationservice.model.AlertMessage;
import com.aiobservability.services.notificationservice.model.AlertNotificationRecord;
import com.aiobservability.services.notificationservice.repository.AlertNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AlertDispatchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlertDispatchService.class);

    private final NotificationProperties properties;
    private final AlertNotificationRepository repository;
    private final Map<String, NotificationChannelClient> clientsByChannel;

    public AlertDispatchService(
            NotificationProperties properties,
            AlertNotificationRepository repository,
            List<NotificationChannelClient> clients
    ) {
        this.properties = properties;
        this.repository = repository;
        this.clientsByChannel = clients.stream()
                .collect(Collectors.toMap(
                        client -> client.channel().toLowerCase(Locale.ROOT),
                        Function.identity()
                ));
    }

    public void dispatch(AlertMessage message, List<String> requestedChannels) {
        List<String> channels = requestedChannels == null || requestedChannels.isEmpty()
                ? properties.channels()
                : requestedChannels;

        for (String channel : channels) {
            String normalized = channel.toLowerCase(Locale.ROOT);
            String dedupKey = dedupKey(message);
            Map<String, Object> persistedPayload = toPersistedPayload(message, normalized, dedupKey);

            if (shouldSuppress(message, normalized, dedupKey)) {
                repository.createSuppressed(
                        message.incidentId(),
                        normalized,
                        persistedPayload,
                        "suppressed duplicate within " + properties.suppressionWindowMinutes() + " minute window"
                );
                continue;
            }

            AlertNotificationRecord record = repository.createPending(message.incidentId(), normalized, persistedPayload);

            NotificationChannelClient client = clientsByChannel.get(normalized);
            if (client == null) {
                repository.markFailed(record.id(), 0, "Unsupported channel: " + normalized);
                continue;
            }

            int maxAttempts = Math.max(1, properties.maxRetries() + 1);
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    client.send(message);
                    repository.markSent(record.id(), attempt);
                    break;
                } catch (Exception ex) {
                    if (attempt < maxAttempts) {
                        repository.markRetrying(record.id(), attempt, safeMessage(ex));
                        sleepRetryDelay();
                    } else {
                        repository.markFailed(record.id(), attempt, safeMessage(ex));
                    }
                }
            }
        }
    }

    public List<AlertNotificationRecord> listAlerts(UUID incidentId, Integer requestedLimit) {
        int limit = normalizeLimit(requestedLimit);
        if (incidentId == null) {
            return repository.findLatest(limit);
        }
        return repository.findByIncidentId(incidentId, limit);
    }

    private int normalizeLimit(Integer requestedLimit) {
        int value = requestedLimit == null ? properties.queryLimitDefault() : requestedLimit;
        if (value <= 0) {
            value = properties.queryLimitDefault();
        }
        return Math.min(value, properties.queryLimitMax());
    }

    private Map<String, Object> toPersistedPayload(AlertMessage message, String channel, String dedupKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel", channel);
        payload.put("incidentId", message.incidentId());
        payload.put("severity", message.severity());
        payload.put("title", message.title());
        payload.put("summary", message.summary());
        payload.put("recommendedActions", message.recommendedActions());
        payload.put("sourceEventType", message.sourceEventType());
        payload.put("occurredAt", message.occurredAt());
        payload.put("dedupKey", dedupKey);
        payload.put("rawPayload", message.rawPayload());
        return payload;
    }

    private boolean shouldSuppress(AlertMessage message, String channel, String dedupKey) {
        if (!properties.suppressionEnabled()) {
            return false;
        }
        Instant since = Instant.now().minusSeconds(properties.suppressionWindowMinutes() * 60L);
        return repository.existsRecentByDedupKey(message.incidentId(), channel, dedupKey, since);
    }

    private String dedupKey(AlertMessage message) {
        return String.join(
                "|",
                valueOrUnknown(message.sourceEventType()),
                message.incidentId().toString(),
                valueOrUnknown(message.severity()),
                valueOrUnknown(message.title())
        );
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private void sleepRetryDelay() {
        if (properties.retryDelayMs() <= 0) {
            return;
        }
        try {
            Thread.sleep(properties.retryDelayMs());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Alert retry sleep interrupted");
        }
    }

    private String safeMessage(Exception exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getMessage();
    }
}
