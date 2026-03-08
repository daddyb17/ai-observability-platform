package com.aiobservability.services.searchqueryservice;

import com.aiobservability.services.searchqueryservice.model.LogSearchFilters;
import com.aiobservability.services.searchqueryservice.service.ElasticsearchLogSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SearchQueryController {
    private final ElasticsearchLogSearchService searchService;

    public SearchQueryController(ElasticsearchLogSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/logs/search")
    public ResponseEntity<Map<String, Object>> searchLogs(
            @RequestParam(name = "serviceName", required = false) String serviceName,
            @RequestParam(name = "level", required = false) String level,
            @RequestParam(name = "traceId", required = false) String traceId,
            @RequestParam(name = "exceptionType", required = false) String exceptionType,
            @RequestParam(name = "text", required = false) String text,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestParam(name = "sort", defaultValue = "timestamp:desc") String sort
    ) {
        LogSearchFilters filters = new LogSearchFilters(
                serviceName,
                level,
                traceId,
                exceptionType,
                text,
                parseInstantOrNull(from),
                parseInstantOrNull(to),
                page,
                size,
                sort
        );
        return ResponseEntity.ok(searchService.searchLogs(filters));
    }

    @GetMapping("/logs/{id}")
    public ResponseEntity<Map<String, Object>> getLogById(@PathVariable("id") String id) {
        return ResponseEntity.ok(searchService.getLogById(id));
    }

    @GetMapping("/logs/trace/{traceId}")
    public ResponseEntity<Map<String, Object>> getLogsByTraceId(
            @PathVariable("traceId") String traceId,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        return ResponseEntity.ok(searchService.getLogsByTraceId(traceId, size));
    }

    @GetMapping("/incidents/{id}")
    public ResponseEntity<Map<String, Object>> getIncident(@PathVariable("id") String id) {
        return ResponseEntity.ok(Map.of("incidentId", id, "status", "OPEN"));
    }

    private Instant parseInstantOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }
}
