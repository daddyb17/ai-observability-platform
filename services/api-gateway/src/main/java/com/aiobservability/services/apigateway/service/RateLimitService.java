package com.aiobservability.services.apigateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitService {
    private final int loginPerMinute;
    private final int searchPerMinute;
    private final int analyzePerMinute;

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitService(
            @Value("${app.gateway.rate-limit.login-per-minute}") int loginPerMinute,
            @Value("${app.gateway.rate-limit.search-per-minute}") int searchPerMinute,
            @Value("${app.gateway.rate-limit.analyze-per-minute}") int analyzePerMinute
    ) {
        this.loginPerMinute = loginPerMinute;
        this.searchPerMinute = searchPerMinute;
        this.analyzePerMinute = analyzePerMinute;
    }

    public boolean isAllowed(String endpointType, String clientKey) {
        int limit = switch (endpointType) {
            case "login" -> loginPerMinute;
            case "search" -> searchPerMinute;
            case "analyze" -> analyzePerMinute;
            default -> Integer.MAX_VALUE;
        };
        if (limit == Integer.MAX_VALUE) {
            return true;
        }

        String key = endpointType + "|" + clientKey;
        Instant now = Instant.now();
        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(now, new AtomicInteger(0)));
        synchronized (counter) {
            if (counter.windowStart().isBefore(now.minus(1, ChronoUnit.MINUTES))) {
                counter.windowStart = now;
                counter.count.set(0);
            }
            return counter.count.incrementAndGet() <= limit;
        }
    }

    private static final class WindowCounter {
        private Instant windowStart;
        private final AtomicInteger count;

        private WindowCounter(Instant windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }

        private Instant windowStart() {
            return windowStart;
        }
    }
}
