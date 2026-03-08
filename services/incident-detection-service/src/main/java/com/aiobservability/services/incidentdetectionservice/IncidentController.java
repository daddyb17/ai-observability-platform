package com.aiobservability.services.incidentdetectionservice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
public class IncidentController {

    @GetMapping("/api/incidents/{id}")
    public ResponseEntity<Map<String, Object>> getIncident(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("incidentId", id, "severity", "MEDIUM", "status", "OPEN"));
    }

    @PatchMapping("/api/incidents/{id}/status")
    public ResponseEntity<Map<String, String>> updateStatus(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("incidentId", id, "status", "ACKNOWLEDGED"));
    }

    @PostMapping("/api/incidents/{id}/analyze")
    public ResponseEntity<Map<String, String>> analyze(@PathVariable String id) {
        return ResponseEntity.accepted().body(Map.of("incidentId", id, "analysis", "queued"));
    }
}
