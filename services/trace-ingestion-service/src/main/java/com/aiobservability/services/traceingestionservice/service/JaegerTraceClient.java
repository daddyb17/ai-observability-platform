package com.aiobservability.services.traceingestionservice.service;

import com.aiobservability.services.traceingestionservice.config.TraceIngestionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class JaegerTraceClient {
    private final TraceIngestionProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public JaegerTraceClient(TraceIngestionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public JsonNode fetchTraceById(String traceId) {
        String endpoint = stripTrailingSlash(properties.jaegerUrl()) + "/api/traces/" + traceId;
        JsonNode root = executeGet(endpoint);
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Trace not found: " + traceId);
        }
        return data.get(0);
    }

    public List<JsonNode> searchByService(String serviceName, int limit) {
        String encodedService = URLEncoder.encode(serviceName, StandardCharsets.UTF_8);
        String endpoint = stripTrailingSlash(properties.jaegerUrl()) + "/api/traces?service=" + encodedService + "&limit=" + limit;
        JsonNode root = executeGet(endpoint);
        JsonNode data = root.path("data");
        List<JsonNode> traces = new ArrayList<>();
        if (data.isArray()) {
            data.forEach(traces::add);
        }
        return traces;
    }

    private JsonNode executeGet(String endpoint) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(BAD_GATEWAY, "Jaeger query failed with status " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to query Jaeger", ex);
        }
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:16686";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
