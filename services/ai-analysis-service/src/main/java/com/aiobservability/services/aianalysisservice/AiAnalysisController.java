package com.aiobservability.services.aianalysisservice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
public class AiAnalysisController {

    @PostMapping("/internal/ai/analyze/{incidentId}")
    public ResponseEntity<Map<String, String>> analyze(@PathVariable String incidentId) {
        return ResponseEntity.accepted().body(Map.of("incidentId", incidentId, "status", "analysis-queued"));
    }

    @GetMapping("/api/incidents/{incidentId}/analysis")
    public ResponseEntity<Map<String, Object>> getAnalysis(@PathVariable String incidentId) {
        return ResponseEntity.ok(Map.of("incidentId", incidentId, "summary", "Analysis pending"));
    }
}
