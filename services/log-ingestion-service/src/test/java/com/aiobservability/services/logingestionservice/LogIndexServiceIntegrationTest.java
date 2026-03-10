package com.aiobservability.services.logingestionservice;

import com.aiobservability.services.logingestionservice.config.LogIngestionProperties;
import com.aiobservability.services.logingestionservice.elasticsearch.LogIndexService;
import com.aiobservability.services.logingestionservice.model.EnrichedLogEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class LogIndexServiceIntegrationTest {
    @Container
    static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.17.22")
                    .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("discovery.type", "single-node");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static LogIndexService logIndexService;
    private static HttpClient httpClient;

    @BeforeAll
    static void setup() {
        LogIngestionProperties properties = new LogIngestionProperties(
                "logs.raw",
                "deadletter.logs",
                3,
                5,
                "http://" + ELASTICSEARCH.getHttpHostAddress(),
                "logs",
                3,
                100
        );
        logIndexService = new LogIndexService(properties, OBJECT_MAPPER);
        httpClient = HttpClient.newHttpClient();
    }

    @Test
    void indexesDocumentAndRetrievesItById() throws Exception {
        String eventId = UUID.randomUUID().toString();
        EnrichedLogEvent event = new EnrichedLogEvent(
                eventId,
                "payment-service",
                "dev",
                Instant.parse("2026-03-08T10:15:30Z"),
                "ERROR",
                "Database connection timeout",
                "SQLTimeoutException",
                "stack",
                "trace-abc-123",
                "span-111",
                "payment-service-1",
                Map.of("version", "1.0.0", "region", "local"),
                Instant.parse("2026-03-08T10:15:31Z")
        );

        String indexName = logIndexService.index(event);
        assertEquals("logs-2026-03", indexName);

        HttpRequest refreshRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://" + ELASTICSEARCH.getHttpHostAddress() + "/" + indexName + "/_refresh"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> refreshResponse = httpClient.send(refreshRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, refreshResponse.statusCode());

        Map<String, Object> searchBody = Map.of(
                "size", 1,
                "query", Map.of(
                        "bool", Map.of(
                                "should", List.of(
                                        Map.of("term", Map.of("eventId", eventId)),
                                        Map.of("term", Map.of("eventId.keyword", eventId))
                                ),
                                "minimum_should_match", 1
                        )
                )
        );
        HttpRequest searchRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://" + ELASTICSEARCH.getHttpHostAddress() + "/" + indexName + "/_search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(searchBody)))
                .build();
        HttpResponse<String> searchResponse = httpClient.send(searchRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, searchResponse.statusCode());

        JsonNode searchRoot = OBJECT_MAPPER.readTree(searchResponse.body());
        JsonNode hits = searchRoot.path("hits").path("hits");
        assertTrue(hits.isArray() && !hits.isEmpty(), "Expected indexed document in search response");

        JsonNode source = hits.get(0).path("_source");
        assertEquals("payment-service", source.path("serviceName").asText());
        assertEquals("SQLTimeoutException", source.path("exceptionType").asText());
        assertEquals("2026-03-08T10:15:30Z", source.path("@timestamp").asText());
        assertEquals("payment-service-1", source.path("host").path("name").asText());
        assertEquals("payment-service-1", source.path("hostName").asText());
    }
}
