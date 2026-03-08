package com.aiobservability.services.apigateway;

import com.aiobservability.services.apigateway.security.GatewayJwtService;
import com.aiobservability.services.apigateway.service.GatewayRoutingService;
import com.aiobservability.services.apigateway.service.HttpForwardService;
import com.aiobservability.services.apigateway.service.RateLimitService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class GatewayProxyController {
    private final GatewayRoutingService gatewayRoutingService;
    private final GatewayJwtService gatewayJwtService;
    private final RateLimitService rateLimitService;
    private final HttpForwardService httpForwardService;

    public GatewayProxyController(
            GatewayRoutingService gatewayRoutingService,
            GatewayJwtService gatewayJwtService,
            RateLimitService rateLimitService,
            HttpForwardService httpForwardService
    ) {
        this.gatewayRoutingService = gatewayRoutingService;
        this.gatewayJwtService = gatewayJwtService;
        this.rateLimitService = rateLimitService;
        this.httpForwardService = httpForwardService;
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(
            HttpMethod method,
            HttpServletRequest request,
            @RequestHeader HttpHeaders requestHeaders,
            @RequestBody(required = false) byte[] body
    ) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/gateway/")) {
            return ResponseEntity.notFound().build();
        }

        String targetBaseUrl = gatewayRoutingService.resolveTargetBaseUrl(path);
        if (targetBaseUrl == null) {
            return ResponseEntity.status(404).body(("No gateway route for " + path).getBytes());
        }

        String clientKey = clientKey(request);
        String rateLimitType = gatewayRoutingService.rateLimitType(path);
        if (!rateLimitService.isAllowed(rateLimitType, clientKey)) {
            return ResponseEntity.status(429).body("Rate limit exceeded".getBytes());
        }

        HttpHeaders forwardedHeaders = new HttpHeaders();
        forwardedHeaders.putAll(requestHeaders);
        String requestId = firstNonBlank(request.getHeader("X-Request-Id"), UUID.randomUUID().toString());
        String traceId = firstNonBlank(request.getHeader("X-Trace-Id"), requestId);
        forwardedHeaders.set("X-Request-Id", requestId);
        forwardedHeaders.set("X-Trace-Id", traceId);

        if (gatewayRoutingService.isProtectedPath(path)) {
            String authorization = firstNonBlank(
                    requestHeaders.getFirst(HttpHeaders.AUTHORIZATION),
                    forwardedHeaders.getFirst(HttpHeaders.AUTHORIZATION),
                    forwardedHeaders.getFirst("authorization")
            );
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body("Missing bearer token".getBytes());
            }
            try {
                Claims claims = gatewayJwtService.parseAndValidateAccessToken(authorization.substring(7));
                forwardedHeaders.set("X-Authenticated-User", claims.getSubject());
                forwardedHeaders.set("X-Authenticated-Role", claims.get("role", String.class));
            } catch (Exception ex) {
                return ResponseEntity.status(401).body("Invalid bearer token".getBytes());
            }
        }

        String targetUrl = targetBaseUrl + path + querySuffix(request);
        ResponseEntity<byte[]> upstreamResponse = httpForwardService.forward(method, targetUrl, forwardedHeaders, body);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(upstreamResponse.getHeaders());
        responseHeaders.set("X-Request-Id", requestId);
        responseHeaders.set("X-Trace-Id", traceId);
        return ResponseEntity.status(upstreamResponse.getStatusCode()).headers(responseHeaders).body(upstreamResponse.getBody());
    }

    private String querySuffix(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null || query.isBlank() ? "" : "?" + query;
    }

    private String clientKey(HttpServletRequest request) {
        return firstNonBlank(request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
