package com.aiobservability.services.apigateway.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
    private final boolean redisEnabled;
    private final StringRedisTemplate redisTemplate;

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitService(
            @Value("${app.gateway.rate-limit.login-per-minute}") int loginPerMinute,
            @Value("${app.gateway.rate-limit.search-per-minute}") int searchPerMinute,
            @Value("${app.gateway.rate-limit.analyze-per-minute}") int analyzePerMinute,
            @Value("${app.gateway.rate-limit.redis-enabled:true}") boolean redisEnabled,
            @Autowired(required = false) StringRedisTemplate redisTemplate
    ) {
        this.loginPerMinute = loginPerMinute;
        this.searchPerMinute = searchPerMinute;
        this.analyzePerMinute = analyzePerMinute;
        this.redisEnabled = redisEnabled;
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String endpointType, String clientKey) {
        int limit = resolveLimit(endpointType);
        if (limit == Integer.MAX_VALUE) {
            return true;
        }

        if (redisEnabled && redisTemplate != null) {
            try {
                String minuteBucket = Long.toString(Instant.now().getEpochSecond() / 60);
                String redisKey = "ratelimit:" + endpointType + ":" + clientKey + ":" + minuteBucket;
                Long count = redisTemplate.opsForValue().increment(redisKey);
                if (count != null && count == 1L) {
                    redisTemplate.expire(redisKey, Duration.ofMinutes(2));
                }
                return count != null && count <= limit;
            } catch (RuntimeException ignored) {
                // Fall back to in-memory mode when Redis is unavailable.
            }
        }
        return isAllowedInMemory(endpointType, clientKey, limit);
    }

    private boolean isAllowedInMemory(String endpointType, String clientKey, int limit) {
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

    private int resolveLimit(String endpointType) {
        return switch (endpointType) {
            case "login" -> loginPerMinute;
            case "search" -> searchPerMinute;
            case "analyze" -> analyzePerMinute;
            default -> Integer.MAX_VALUE;
        };
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
