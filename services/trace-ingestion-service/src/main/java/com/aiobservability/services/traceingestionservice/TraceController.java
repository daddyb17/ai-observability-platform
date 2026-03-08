package com.aiobservability.services.traceingestionservice;

import com.aiobservability.services.traceingestionservice.model.TraceSummarizeRequest;
import com.aiobservability.services.traceingestionservice.model.TraceSummaryDocument;
import com.aiobservability.services.traceingestionservice.service.TraceSummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class TraceController {
    private final TraceSummaryService traceSummaryService;

    public TraceController(TraceSummaryService traceSummaryService) {
        this.traceSummaryService = traceSummaryService;
    }

    @GetMapping("/api/traces/{traceId}")
    public ResponseEntity<TraceSummaryDocument> getTrace(@PathVariable("traceId") String traceId) {
        return ResponseEntity.ok(traceSummaryService.getTraceSummary(traceId));
    }

    @GetMapping("/api/traces/service/{serviceName}")
    public ResponseEntity<Map<String, Object>> getByService(
            @PathVariable("serviceName") String serviceName,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        List<TraceSummaryDocument> summaries = traceSummaryService.getTraceSummariesForService(serviceName, limit);
        return ResponseEntity.ok(Map.of(
                "serviceName", serviceName,
                "count", summaries.size(),
                "items", summaries
        ));
    }

    @GetMapping("/api/traces/incident/{incidentId}")
    public ResponseEntity<Map<String, Object>> getByIncident(
            @PathVariable("incidentId") String incidentId,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        List<TraceSummaryDocument> summaries = traceSummaryService.getTraceSummariesForIncident(incidentId, limit);
        return ResponseEntity.ok(Map.of(
                "incidentId", incidentId,
                "count", summaries.size(),
                "items", summaries
        ));
    }

    @PostMapping("/internal/traces/summarize")
    public ResponseEntity<TraceSummaryDocument> summarize(@RequestBody TraceSummarizeRequest request) {
        TraceSummaryDocument summary = traceSummaryService.summarize(request);
        return ResponseEntity.accepted().body(summary);
    }
}
