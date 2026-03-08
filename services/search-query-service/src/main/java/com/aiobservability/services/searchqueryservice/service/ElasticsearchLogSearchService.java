package com.aiobservability.services.searchqueryservice.service;

import com.aiobservability.services.searchqueryservice.config.SearchProperties;
import com.aiobservability.services.searchqueryservice.model.LogSearchFilters;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ElasticsearchLogSearchService {
    private final SearchProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ElasticsearchLogSearchService(SearchProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public Map<String, Object> searchLogs(LogSearchFilters filters) {
        int page = Math.max(0, filters.page());
        int size = normalizeSize(filters.size());
        SortSpec sortSpec = parseSort(filters.sort());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", page * size);
        body.put("size", size);
        body.put("sort", List.of(Map.of(sortSpec.field, Map.of("order", sortSpec.order))));
        body.put("query", buildQuery(filters));

        JsonNode root = search(body);
        JsonNode hitsNode = root.path("hits").path("hits");

        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode hit : hitsNode) {
            Map<String, Object> item = objectMapper.convertValue(hit.path("_source"), new TypeReference<>() {
            });
            item.putIfAbsent("id", hit.path("_id").asText(null));
            items.add(item);
        }

        long total = extractTotal(root.path("hits").path("total"));
        return Map.of(
                "page", page,
                "size", size,
                "total", total,
                "items", items
        );
    }

    public Map<String, Object> getLogById(String id) {
        Map<String, Object> body = Map.of(
                "size", 1,
                "query", Map.of("ids", Map.of("values", List.of(id)))
        );
        JsonNode root = search(body);
        JsonNode hits = root.path("hits").path("hits");
        if (!hits.isArray() || hits.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Log not found: " + id);
        }
        JsonNode first = hits.get(0);
        Map<String, Object> item = objectMapper.convertValue(first.path("_source"), new TypeReference<>() {
        });
        item.putIfAbsent("id", first.path("_id").asText(id));
        return item;
    }

    public Map<String, Object> getLogsByTraceId(String traceId, Integer sizeParam) {
        int size = normalizeSize(sizeParam == null ? properties.defaultPageSize() : sizeParam);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", size);
        body.put("sort", List.of(Map.of("timestamp", Map.of("order", "desc"))));
        body.put("query", Map.of("bool", Map.of(
                "filter", List.of(Map.of("term", Map.of("traceId", traceId)))
        )));

        JsonNode root = search(body);
        JsonNode hitsNode = root.path("hits").path("hits");
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode hit : hitsNode) {
            Map<String, Object> item = objectMapper.convertValue(hit.path("_source"), new TypeReference<>() {
            });
            item.putIfAbsent("id", hit.path("_id").asText(null));
            items.add(item);
        }

        return Map.of(
                "traceId", traceId,
                "size", size,
                "total", extractTotal(root.path("hits").path("total")),
                "items", items
        );
    }

    private JsonNode search(Map<String, Object> body) {
        String endpoint = stripTrailingSlash(properties.elasticsearchUrl()) + "/" + properties.logIndexPattern() + "/_search";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readTree(response.body());
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Elasticsearch query failed with status " + response.statusCode() + ": " + response.body()
            );
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to query Elasticsearch", ex);
        }
    }

    private Map<String, Object> buildQuery(LogSearchFilters filters) {
        List<Map<String, Object>> filterClauses = new ArrayList<>();
        List<Map<String, Object>> mustClauses = new ArrayList<>();

        addTermFilter(filterClauses, "serviceName", filters.serviceName());
        addTermFilter(filterClauses, "level", normalizeLevel(filters.level()));
        addTermFilter(filterClauses, "traceId", filters.traceId());
        addTermFilter(filterClauses, "exceptionType", filters.exceptionType());
        addRangeFilter(filterClauses, filters.from(), filters.to());

        if (filters.text() != null && !filters.text().isBlank()) {
            mustClauses.add(Map.of(
                    "simple_query_string",
                    Map.of(
                            "query", filters.text(),
                            "fields", List.of("message", "stackTrace"),
                            "default_operator", "and"
                    )
            ));
        }

        Map<String, Object> boolQuery = new LinkedHashMap<>();
        if (!filterClauses.isEmpty()) {
            boolQuery.put("filter", filterClauses);
        }
        if (!mustClauses.isEmpty()) {
            boolQuery.put("must", mustClauses);
        }
        if (boolQuery.isEmpty()) {
            return Map.of("match_all", Map.of());
        }
        return Map.of("bool", boolQuery);
    }

    private void addTermFilter(List<Map<String, Object>> filterClauses, String field, String value) {
        if (value != null && !value.isBlank()) {
            filterClauses.add(Map.of("term", Map.of(field, value)));
        }
    }

    private void addRangeFilter(List<Map<String, Object>> filterClauses, Instant from, Instant to) {
        if (from == null && to == null) {
            return;
        }
        Map<String, Object> rangeValues = new LinkedHashMap<>();
        if (from != null) {
            rangeValues.put("gte", from.toString());
        }
        if (to != null) {
            rangeValues.put("lte", to.toString());
        }
        filterClauses.add(Map.of("range", Map.of("timestamp", rangeValues)));
    }

    private String normalizeLevel(String level) {
        if (level == null || level.isBlank()) {
            return null;
        }
        String normalized = level.trim().toUpperCase(Locale.ROOT);
        if ("WARNING".equals(normalized)) {
            return "WARN";
        }
        if ("FATAL".equals(normalized)) {
            return "ERROR";
        }
        return normalized;
    }

    private int normalizeSize(int requestedSize) {
        int size = requestedSize <= 0 ? properties.defaultPageSize() : requestedSize;
        return Math.min(size, properties.maxPageSize());
    }

    private SortSpec parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return new SortSpec("timestamp", "desc");
        }
        String[] parts = sort.split(":", 2);
        String field = parts[0].isBlank() ? "timestamp" : parts[0];
        String order = parts.length == 2 ? parts[1].toLowerCase(Locale.ROOT) : "desc";
        if (!"asc".equals(order) && !"desc".equals(order)) {
            order = "desc";
        }
        return new SortSpec(field, order);
    }

    private long extractTotal(JsonNode totalNode) {
        if (totalNode == null || totalNode.isMissingNode()) {
            return 0L;
        }
        if (totalNode.isNumber()) {
            return totalNode.asLong(0L);
        }
        return totalNode.path("value").asLong(0L);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize Elasticsearch request", ex);
        }
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:9200";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record SortSpec(String field, String order) {
    }
}
