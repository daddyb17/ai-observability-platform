package com.aiobservability.services.incidentdetectionservice;

import com.aiobservability.services.incidentdetectionservice.model.IncidentRecord;
import com.aiobservability.services.incidentdetectionservice.model.IncidentSignalRecord;
import com.aiobservability.services.incidentdetectionservice.model.IncidentRulesUpdateRequest;
import com.aiobservability.services.incidentdetectionservice.model.IncidentTimelineEntry;
import com.aiobservability.services.incidentdetectionservice.model.IncidentStatusUpdateRequest;
import com.aiobservability.services.incidentdetectionservice.service.IncidentService;
import com.aiobservability.shared.models.IncidentStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping
public class IncidentController {
    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping("/api/incidents")
    public ResponseEntity<List<IncidentRecord>> listIncidents() {
        return ResponseEntity.ok(incidentService.listIncidents());
    }

    @GetMapping("/api/incidents/{id}")
    public ResponseEntity<IncidentRecord> getIncident(@PathVariable("id") String id) {
        return ResponseEntity.ok(incidentService.getIncident(parseIncidentId(id)));
    }

    @GetMapping("/api/incidents/{id}/signals")
    public ResponseEntity<List<IncidentSignalRecord>> getIncidentSignals(@PathVariable("id") String id) {
        return ResponseEntity.ok(incidentService.getIncidentSignals(parseIncidentId(id)));
    }

    @GetMapping("/api/incidents/{id}/timeline")
    public ResponseEntity<List<IncidentTimelineEntry>> getIncidentTimeline(@PathVariable("id") String id) {
        return ResponseEntity.ok(incidentService.getIncidentTimeline(parseIncidentId(id)));
    }

    @PatchMapping("/api/incidents/{id}/status")
    public ResponseEntity<IncidentRecord> updateStatus(
            @PathVariable("id") String id,
            @RequestBody IncidentStatusUpdateRequest request
    ) {
        if (request == null || request.status() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "status is required");
        }
        return ResponseEntity.ok(incidentService.updateStatus(parseIncidentId(id), request.status()));
    }

    @PostMapping("/api/incidents/{id}/analyze")
    public ResponseEntity<Map<String, String>> analyze(@PathVariable("id") String id) {
        UUID incidentId = parseIncidentId(id);
        incidentService.requestAnalysis(incidentId);
        return ResponseEntity.accepted().body(Map.of(
                "incidentId", incidentId.toString(),
                "analysis", "queued"
        ));
    }

    @GetMapping("/internal/incidents/rules")
    public ResponseEntity<Map<String, Object>> rules() {
        return ResponseEntity.ok(incidentService.rulesSummary());
    }

    @PatchMapping("/internal/incidents/rules")
    public ResponseEntity<Map<String, Object>> updateRules(
            @RequestBody(required = false) IncidentRulesUpdateRequest request
    ) {
        try {
            return ResponseEntity.ok(incidentService.updateRules(request));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/internal/incidents/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate() {
        return ResponseEntity.ok(incidentService.evaluateSummary());
    }

    private UUID parseIncidentId(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "incident id must be UUID");
        }
    }
}
