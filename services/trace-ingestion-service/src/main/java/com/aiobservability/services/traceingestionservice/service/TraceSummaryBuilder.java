package com.aiobservability.services.traceingestionservice.service;

import com.aiobservability.services.traceingestionservice.model.TraceSummaryDocument;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Service
public class TraceSummaryBuilder {

    public TraceSummaryDocument build(JsonNode traceNode, String overrideTraceId, String incidentId) {
        JsonNode spans = traceNode.path("spans");
        if (!spans.isArray() || spans.isEmpty()) {
            String traceId = overrideTraceId != null ? overrideTraceId : traceNode.path("traceID").asText();
            return new TraceSummaryDocument(traceId, incidentId, "unknown", 0L, false, 0, null, null, Instant.now());
        }

        Map<String, String> processToService = parseProcessMap(traceNode.path("processes"));
        long minStartMicros = Long.MAX_VALUE;
        long maxEndMicros = 0L;
        long maxDurationMicros = -1L;
        String bottleneckService = null;
        String bottleneckSpan = null;
        String rootService = null;
        boolean errorFlag = false;

        for (JsonNode span : spans) {
            long startMicros = span.path("startTime").asLong(0L);
            long durationMicros = span.path("duration").asLong(0L);
            long endMicros = startMicros + durationMicros;
            minStartMicros = Math.min(minStartMicros, startMicros);
            maxEndMicros = Math.max(maxEndMicros, endMicros);

            String serviceName = processToService.getOrDefault(span.path("processID").asText(), "unknown");
            String operationName = span.path("operationName").asText("unknown-operation");

            if (isRootSpan(span) && rootService == null) {
                rootService = serviceName;
            }

            if (durationMicros > maxDurationMicros) {
                maxDurationMicros = durationMicros;
                bottleneckService = serviceName;
                bottleneckSpan = operationName;
            }

            if (spanHasError(span.path("tags"))) {
                errorFlag = true;
            }
        }

        if (rootService == null) {
            JsonNode firstSpan = spans.get(0);
            rootService = processToService.getOrDefault(firstSpan.path("processID").asText(), "unknown");
        }

        String traceId = overrideTraceId;
        if (traceId == null || traceId.isBlank()) {
            traceId = traceNode.path("traceID").asText();
        }

        long durationMs = minStartMicros == Long.MAX_VALUE ? 0L : Math.max(0L, (maxEndMicros - minStartMicros) / 1000L);
        Instant startedAt = minStartMicros == Long.MAX_VALUE
                ? Instant.now()
                : Instant.ofEpochMilli(minStartMicros / 1000L);

        return new TraceSummaryDocument(
                traceId,
                incidentId,
                rootService,
                durationMs,
                errorFlag,
                spans.size(),
                bottleneckService,
                bottleneckSpan,
                startedAt
        );
    }

    private Map<String, String> parseProcessMap(JsonNode processesNode) {
        Map<String, String> processToService = new HashMap<>();
        if (!processesNode.isObject()) {
            return processToService;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = processesNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            processToService.put(field.getKey(), field.getValue().path("serviceName").asText("unknown"));
        }
        return processToService;
    }

    private boolean isRootSpan(JsonNode span) {
        JsonNode references = span.path("references");
        return !references.isArray() || references.isEmpty();
    }

    private boolean spanHasError(JsonNode tags) {
        if (!tags.isArray()) {
            return false;
        }
        for (JsonNode tag : tags) {
            String key = tag.path("key").asText();
            if ("error".equals(key) && "true".equalsIgnoreCase(tag.path("value").asText())) {
                return true;
            }
            if ("otel.status_code".equals(key) && "ERROR".equalsIgnoreCase(tag.path("value").asText())) {
                return true;
            }
        }
        return false;
    }
}
