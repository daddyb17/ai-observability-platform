package com.aiobservability.services.traceingestionservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class InternalEndpointAuthInterceptor implements HandlerInterceptor {
    private final String internalApiToken;

    public InternalEndpointAuthInterceptor(@Value("${app.internal.auth-token:}") String internalApiToken) {
        this.internalApiToken = internalApiToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!request.getRequestURI().startsWith("/internal/")) {
            return true;
        }
        if (internalApiToken == null || internalApiToken.isBlank()) {
            response.sendError(
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "Internal endpoint auth token is not configured"
            );
            return false;
        }
        if (!internalApiToken.equals(request.getHeader("X-Internal-Auth-Token"))) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "Forbidden");
            return false;
        }
        return true;
    }
}
