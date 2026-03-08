package com.aiobservability.services.metricsingestionservice.service;

import com.aiobservability.services.metricsingestionservice.config.MetricsIngestionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.OptionalDouble;

@Service
public class PrometheusQueryClient {
    private final MetricsIngestionProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PrometheusQueryClient(MetricsIngestionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public OptionalDouble queryScalar(String promQl) {
        String baseUrl = stripTrailingSlash(properties.prometheusUrl());
        String endpoint = baseUrl + "/api/v1/query?query=" + URLEncoder.encode(promQl, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return OptionalDouble.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!"success".equals(root.path("status").asText())) {
                return OptionalDouble.empty();
            }
            JsonNode results = root.path("data").path("result");
            if (!results.isArray() || results.isEmpty()) {
                return OptionalDouble.empty();
            }
            String value = results.get(0).path("value").get(1).asText();
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(parsed);
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return OptionalDouble.empty();
        }
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:9090";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
