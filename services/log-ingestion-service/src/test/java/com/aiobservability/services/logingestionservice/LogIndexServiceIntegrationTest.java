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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + ELASTICSEARCH.getHttpHostAddress() + "/" + indexName + "/_doc/" + eventId))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        JsonNode root = OBJECT_MAPPER.readTree(response.body());
        assertTrue(root.path("found").asBoolean());
        assertEquals("payment-service", root.path("_source").path("serviceName").asText());
        assertEquals("SQLTimeoutException", root.path("_source").path("exceptionType").asText());
    }
}
