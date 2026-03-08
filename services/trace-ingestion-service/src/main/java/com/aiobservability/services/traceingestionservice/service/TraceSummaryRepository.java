package com.aiobservability.services.traceingestionservice.service;

import com.aiobservability.services.traceingestionservice.config.TraceIngestionProperties;
import com.aiobservability.services.traceingestionservice.model.TraceSummaryDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Service
public class TraceSummaryRepository {
    private final TraceIngestionProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public TraceSummaryRepository(TraceIngestionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void save(TraceSummaryDocument summary) {
        String endpoint = baseEs() + "/" + properties.summaryIndex() + "/_doc/" + summary.traceId();
        try {
            String body = objectMapper.writeValueAsString(summary);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(BAD_GATEWAY, "Failed to save trace summary");
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to save trace summary", ex);
        }
    }

    public TraceSummaryDocument findByTraceId(String traceId) {
        String endpoint = baseEs() + "/" + properties.summaryIndex() + "/_doc/" + traceId;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(BAD_GATEWAY, "Failed to fetch trace summary");
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("found").asBoolean(false)) {
                return null;
            }
            return objectMapper.convertValue(root.path("_source"), TraceSummaryDocument.class);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to fetch trace summary", ex);
        }
    }

    public List<TraceSummaryDocument> findByIncidentId(String incidentId, int limit) {
        Map<String, Object> body = Map.of(
                "size", limit,
                "sort", List.of(Map.of("startedAt", Map.of("order", "desc"))),
                "query", Map.of("match_phrase", Map.of("incidentId", incidentId))
        );
        return search(body);
    }

    public List<TraceSummaryDocument> findByRootService(String serviceName, int limit) {
        Map<String, Object> body = Map.of(
                "size", limit,
                "sort", List.of(Map.of("startedAt", Map.of("order", "desc"))),
                "query", Map.of("term", Map.of("rootService", serviceName))
        );
        return search(body);
    }

    private List<TraceSummaryDocument> search(Map<String, Object> body) {
        String endpoint = baseEs() + "/" + properties.summaryIndex() + "/_search";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(BAD_GATEWAY, "Failed to search trace summaries");
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<TraceSummaryDocument> results = new ArrayList<>();
            JsonNode hits = root.path("hits").path("hits");
            if (hits.isArray()) {
                for (JsonNode hit : hits) {
                    results.add(objectMapper.convertValue(hit.path("_source"), TraceSummaryDocument.class));
                }
            }
            return results;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to search trace summaries", ex);
        }
    }

    private String baseEs() {
        String value = properties.elasticsearchUrl();
        if (value == null || value.isBlank()) {
            return "http://localhost:9200";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
