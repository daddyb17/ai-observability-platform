package com.aiobservability.services.apigateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GatewayRoutingService {
    private final String authServiceUrl;
    private final String incidentServiceUrl;
    private final String searchServiceUrl;
    private final String metricsServiceUrl;
    private final String traceServiceUrl;
    private final String notificationServiceUrl;

    public GatewayRoutingService(
            @Value("${app.gateway.routes.auth-service-url}") String authServiceUrl,
            @Value("${app.gateway.routes.incident-service-url}") String incidentServiceUrl,
            @Value("${app.gateway.routes.search-service-url}") String searchServiceUrl,
            @Value("${app.gateway.routes.metrics-service-url}") String metricsServiceUrl,
            @Value("${app.gateway.routes.trace-service-url}") String traceServiceUrl,
            @Value("${app.gateway.routes.notification-service-url}") String notificationServiceUrl
    ) {
        this.authServiceUrl = authServiceUrl;
        this.incidentServiceUrl = incidentServiceUrl;
        this.searchServiceUrl = searchServiceUrl;
        this.metricsServiceUrl = metricsServiceUrl;
        this.traceServiceUrl = traceServiceUrl;
        this.notificationServiceUrl = notificationServiceUrl;
    }

    public String resolveTargetBaseUrl(String path) {
        if (path.startsWith("/api/auth/")) {
            return authServiceUrl;
        }
        if (path.startsWith("/api/incidents/")) {
            return incidentServiceUrl;
        }
        if (path.startsWith("/api/logs/")) {
            return searchServiceUrl;
        }
        if (path.startsWith("/api/metrics/")) {
            return metricsServiceUrl;
        }
        if (path.startsWith("/api/traces/")) {
            return traceServiceUrl;
        }
        if (path.startsWith("/api/alerts/")) {
            return notificationServiceUrl;
        }
        return null;
    }

    public boolean isProtectedPath(String path) {
        if (!path.startsWith("/api/")) {
            return false;
        }
        return !path.equals("/api/auth/login")
                && !path.equals("/api/auth/refresh")
                && !path.equals("/api/gateway/health");
    }

    public String rateLimitType(String path) {
        if (path.equals("/api/auth/login")) {
            return "login";
        }
        if (path.startsWith("/api/logs/search")) {
            return "search";
        }
        if (path.matches("^/api/incidents/[^/]+/analyze$")) {
            return "analyze";
        }
        return "none";
    }
}
