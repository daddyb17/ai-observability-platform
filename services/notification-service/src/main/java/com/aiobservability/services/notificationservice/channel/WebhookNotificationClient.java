package com.aiobservability.services.notificationservice.channel;

import com.aiobservability.services.notificationservice.config.NotificationProperties;
import com.aiobservability.services.notificationservice.model.AlertMessage;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class WebhookNotificationClient implements NotificationChannelClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookNotificationClient.class);
    private final NotificationProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public WebhookNotificationClient(NotificationProperties properties) {
        this.properties = properties;
        this.circuitBreaker = CircuitBreaker.of(
                "webhookNotifications",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50.0f)
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .waitDurationInOpenState(Duration.ofSeconds(20))
                        .build()
        );
        this.retry = Retry.of(
                "webhookNotifications",
                RetryConfig.custom()
                        .maxAttempts(Math.max(1, properties.maxRetries() + 1))
                        .waitDuration(Duration.ofMillis(Math.max(0, properties.retryDelayMs())))
                        .retryExceptions(RuntimeException.class)
                        .build()
        );
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

        Supplier<Void> operation = Retry.decorateSupplier(
                retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
                    RestClient.create().post()
                            .uri(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(payload)
                            .retrieve()
                            .toBodilessEntity();
                    return null;
                })
        );
        try {
            operation.get();
        } catch (CallNotPermittedException ex) {
            throw new IllegalStateException("Webhook circuit is open; delivery suppressed", ex);
        }
    }
}
