package com.aiobservability.services.logingestionservice;

import com.aiobservability.services.logingestionservice.model.BatchIngestionResponse;
import com.aiobservability.services.logingestionservice.model.LogIngestionRequest;
import com.aiobservability.services.logingestionservice.model.LogIngestionResult;
import com.aiobservability.services.logingestionservice.service.LogIngestionOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@RestController
@RequestMapping("/internal/logs")
public class LogIngestionController {
    private final LogIngestionOrchestrator orchestrator;

    public LogIngestionController(LogIngestionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody LogIngestionRequest payload) {
        try {
            LogIngestionResult result = orchestrator.ingest(payload);
            return ResponseEntity.accepted().body(Map.of(
                    "status", result.accepted() ? "accepted" : "failed",
                    "eventId", result.eventId()
            ));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Log ingestion failed", ex);
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchIngestionResponse> ingestBatch(@RequestBody List<LogIngestionRequest> payloads) {
        int accepted = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < payloads.size(); i++) {
            LogIngestionRequest request = payloads.get(i);
            try {
                LogIngestionResult result = orchestrator.ingest(request);
                if (result.accepted()) {
                    accepted++;
                } else {
                    failed++;
                    errors.add("Record " + i + " failed");
                }
            } catch (Exception ex) {
                failed++;
                errors.add("Record " + i + " failed: " + safeMessage(ex));
            }
        }

        return ResponseEntity.accepted().body(new BatchIngestionResponse(accepted, failed, errors));
    }

    private String safeMessage(Exception ex) {
        if (ex instanceof ResponseStatusException responseStatusException) {
            return responseStatusException.getReason();
        }
        return ex.getMessage();
    }
}
