package com.aiobservability.services.logingestionservice.elasticsearch;

import com.aiobservability.services.logingestionservice.config.LogIngestionProperties;
import com.aiobservability.services.logingestionservice.model.EnrichedLogEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LogIndexService {
    private static final DateTimeFormatter INDEX_SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final LogIngestionProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public LogIndexService(LogIngestionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public String index(EnrichedLogEvent logEvent) {
        String indexName = properties.indexPrefix() + "-" + INDEX_SUFFIX_FORMATTER.format(logEvent.timestamp());
        String endpoint = stripTrailingSlash(properties.elasticsearchUrl())
                + "/" + indexName + "/_doc/" + logEvent.eventId()
                + "?op_type=create";
        String body = toJson(document(logEvent));

        Exception lastFailure = null;
        for (int attempt = 1; attempt <= properties.elasticsearchMaxAttempts(); attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return indexName;
                }
                if (!isRetryable(response.statusCode())) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "Elasticsearch indexing failed with status " + response.statusCode() + ": " + response.body()
                    );
                }
                lastFailure = new IllegalStateException(
                        "Elasticsearch retryable status " + response.statusCode() + ": " + response.body()
                );
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                lastFailure = ex;
            }
            sleepBackoff();
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Elasticsearch indexing failed after retries",
                lastFailure
        );
    }

    private Map<String, Object> document(EnrichedLogEvent logEvent) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("eventId", logEvent.eventId());
        doc.put("timestamp", logEvent.timestamp());
        doc.put("ingestionTimestamp", logEvent.ingestionTimestamp());
        doc.put("serviceName", logEvent.serviceName());
        doc.put("environment", logEvent.environment());
        doc.put("level", logEvent.level());
        doc.put("message", logEvent.message());
        doc.put("exceptionType", logEvent.exceptionType());
        doc.put("stackTrace", logEvent.stackTrace());
        doc.put("traceId", logEvent.traceId());
        doc.put("spanId", logEvent.spanId());
        doc.put("host", logEvent.host());
        doc.put("tags", logEvent.tags());
        return doc;
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize Elasticsearch document", ex);
        }
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(properties.elasticsearchBackoffMs());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:9200";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
