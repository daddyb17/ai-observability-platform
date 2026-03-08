package com.aiobservability.services.aianalysisservice;

import com.aiobservability.services.aianalysisservice.model.AiAnalysisRecord;
import com.aiobservability.services.aianalysisservice.service.AiAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping
public class AiAnalysisController {
    private final AiAnalysisService aiAnalysisService;

    public AiAnalysisController(AiAnalysisService aiAnalysisService) {
        this.aiAnalysisService = aiAnalysisService;
    }

    @PostMapping("/internal/ai/analyze/{incidentId}")
    public ResponseEntity<AiAnalysisRecord> analyze(@PathVariable("incidentId") String incidentId) {
        AiAnalysisRecord analysis = aiAnalysisService.analyzeIncident(parseIncidentId(incidentId), "manual");
        return ResponseEntity.accepted().body(analysis);
    }

    @GetMapping("/api/incidents/{incidentId}/analysis")
    public ResponseEntity<Map<String, Object>> getAnalysis(@PathVariable("incidentId") String incidentId) {
        UUID parsed = parseIncidentId(incidentId);
        AiAnalysisRecord analysis = aiAnalysisService.getLatestAnalysis(parsed);
        if (analysis == null) {
            return ResponseEntity.ok(Map.of(
                    "incidentId", parsed.toString(),
                    "status", "PENDING"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "incidentId", parsed.toString(),
                "provider", analysis.provider(),
                "modelName", analysis.modelName(),
                "summary", analysis.summary(),
                "rootCause", analysis.rootCause(),
                "confidence", analysis.confidence(),
                "recommendedActions", analysis.recommendedActions(),
                "evidence", analysis.evidence(),
                "createdAt", analysis.createdAt()
        ));
    }

    @GetMapping("/internal/ai/providers")
    public ResponseEntity<List<Map<String, Object>>> providers() {
        return ResponseEntity.ok(aiAnalysisService.getProviderStatus());
    }

    private UUID parseIncidentId(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "incident id must be UUID");
        }
    }
}
