package com.aiobservability.services.traceingestionservice.service;

import com.aiobservability.services.traceingestionservice.config.TraceIngestionProperties;
import com.aiobservability.services.traceingestionservice.kafka.TraceSummaryPublisher;
import com.aiobservability.services.traceingestionservice.model.TraceSummarizeRequest;
import com.aiobservability.services.traceingestionservice.model.TraceSummaryDocument;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TraceSummaryService {
    private final TraceIngestionProperties properties;
    private final JaegerTraceClient jaegerTraceClient;
    private final TraceSummaryBuilder traceSummaryBuilder;
    private final TraceSummaryRepository traceSummaryRepository;
    private final TraceSummaryPublisher traceSummaryPublisher;

    public TraceSummaryService(
            TraceIngestionProperties properties,
            JaegerTraceClient jaegerTraceClient,
            TraceSummaryBuilder traceSummaryBuilder,
            TraceSummaryRepository traceSummaryRepository,
            TraceSummaryPublisher traceSummaryPublisher
    ) {
        this.properties = properties;
        this.jaegerTraceClient = jaegerTraceClient;
        this.traceSummaryBuilder = traceSummaryBuilder;
        this.traceSummaryRepository = traceSummaryRepository;
        this.traceSummaryPublisher = traceSummaryPublisher;
    }

    public TraceSummaryDocument summarize(TraceSummarizeRequest request) {
        String traceId = request.traceId();
        JsonNode traceNode = jaegerTraceClient.fetchTraceById(traceId);
        TraceSummaryDocument summary = traceSummaryBuilder.build(traceNode, traceId, request.incidentId());
        traceSummaryRepository.save(summary);
        traceSummaryPublisher.publish(summary);
        return summary;
    }

    public TraceSummaryDocument getTraceSummary(String traceId) {
        TraceSummaryDocument existing = traceSummaryRepository.findByTraceId(traceId);
        if (existing != null) {
            return existing;
        }
        JsonNode traceNode = jaegerTraceClient.fetchTraceById(traceId);
        TraceSummaryDocument summary = traceSummaryBuilder.build(traceNode, traceId, null);
        traceSummaryRepository.save(summary);
        return summary;
    }

    public List<TraceSummaryDocument> getTraceSummariesForService(String serviceName, Integer requestedLimit) {
        int limit = normalizeLimit(requestedLimit);
        List<JsonNode> traces = jaegerTraceClient.searchByService(serviceName, limit);
        List<TraceSummaryDocument> summaries = new ArrayList<>();
        for (JsonNode trace : traces) {
            TraceSummaryDocument summary = traceSummaryBuilder.build(trace, trace.path("traceID").asText(), null);
            traceSummaryRepository.save(summary);
            summaries.add(summary);
        }
        return summaries;
    }

    public List<TraceSummaryDocument> getTraceSummariesForIncident(String incidentId, Integer requestedLimit) {
        int limit = normalizeLimit(requestedLimit);
        return traceSummaryRepository.findByIncidentId(incidentId, limit);
    }

    private int normalizeLimit(Integer requestedLimit) {
        int value = requestedLimit == null ? properties.searchLimitDefault() : requestedLimit;
        if (value <= 0) {
            value = properties.searchLimitDefault();
        }
        return Math.min(value, properties.searchLimitMax());
    }
}
