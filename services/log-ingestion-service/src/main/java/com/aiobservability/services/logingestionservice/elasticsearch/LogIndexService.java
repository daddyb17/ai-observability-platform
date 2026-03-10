package com.aiobservability.services.logingestionservice.elasticsearch;

import com.aiobservability.services.logingestionservice.config.LogIngestionProperties;
import com.aiobservability.services.logingestionservice.model.EnrichedLogEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
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
import java.util.function.Supplier;

@Service
public class LogIndexService {
    private static final DateTimeFormatter INDEX_SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final LogIngestionProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public LogIndexService(LogIngestionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        // Ensure Java time types (Instant, OffsetDateTime, etc.) always serialize correctly,
        // even when the mapper comes from non-Spring contexts (e.g. integration tests).
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.circuitBreaker = CircuitBreaker.of(
                "elasticsearchIndex",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50.0f)
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .waitDurationInOpenState(Duration.ofSeconds(20))
                        .build()
        );
        this.retry = Retry.of(
                "elasticsearchIndex",
                RetryConfig.custom()
                        .maxAttempts(Math.max(1, properties.elasticsearchMaxAttempts()))
                        .waitDuration(Duration.ofMillis(Math.max(0, properties.elasticsearchBackoffMs())))
                        .retryExceptions(IOException.class, IllegalStateException.class)
                        .build()
        );
    }

    public String index(EnrichedLogEvent logEvent) {
        String indexName = properties.indexPrefix() + "-" + INDEX_SUFFIX_FORMATTER.format(logEvent.timestamp());
        String endpoint = stripTrailingSlash(properties.elasticsearchUrl())
                + "/" + indexName + "/_doc/" + logEvent.eventId()
                + "?op_type=create";
        String body = toJson(document(logEvent));

        Supplier<String> operation = Retry.decorateSupplier(
                retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> doIndexRequest(endpoint, body, indexName))
        );
        try {
            return operation.get();
        } catch (CallNotPermittedException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Elasticsearch circuit is open; indexing temporarily blocked",
                    ex
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Elasticsearch indexing failed after resilience retries",
                    ex
            );
        }
    }

    private String doIndexRequest(String endpoint, String body, String indexName) {
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
            throw new IllegalStateException(
                    "Elasticsearch retryable status " + response.statusCode() + ": " + response.body()
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Elasticsearch I/O failure", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Elasticsearch indexing interrupted", ex);
        }
    }

    private Map<String, Object> document(EnrichedLogEvent logEvent) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("eventId", logEvent.eventId());
        // Keep ECS-style timestamp for environments where logs-* resolves to a data stream.
        doc.put("@timestamp", logEvent.timestamp());
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
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("name", logEvent.host());
        doc.put("host", host);
        doc.put("hostName", logEvent.host());
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

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:9200";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
