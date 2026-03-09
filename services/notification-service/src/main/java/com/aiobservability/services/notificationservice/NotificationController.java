package com.aiobservability.services.notificationservice;

import com.aiobservability.services.notificationservice.model.AlertMessage;
import com.aiobservability.services.notificationservice.model.AlertNotificationRecord;
import com.aiobservability.services.notificationservice.model.AlertSendRequest;
import com.aiobservability.services.notificationservice.model.DlqReplayRequest;
import com.aiobservability.services.notificationservice.repository.IncidentLookupRepository;
import com.aiobservability.services.notificationservice.service.AlertDispatchService;
import com.aiobservability.services.notificationservice.service.DlqReplayService;
import com.aiobservability.services.notificationservice.service.NotificationEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping
public class NotificationController {
    private final AlertDispatchService alertDispatchService;
    private final NotificationEventService notificationEventService;
    private final IncidentLookupRepository incidentLookupRepository;
    private final DlqReplayService dlqReplayService;

    public NotificationController(
            AlertDispatchService alertDispatchService,
            NotificationEventService notificationEventService,
            IncidentLookupRepository incidentLookupRepository,
            DlqReplayService dlqReplayService
    ) {
        this.alertDispatchService = alertDispatchService;
        this.notificationEventService = notificationEventService;
        this.incidentLookupRepository = incidentLookupRepository;
        this.dlqReplayService = dlqReplayService;
    }

    @GetMapping("/api/alerts")
    public ResponseEntity<List<AlertNotificationRecord>> listAlerts(
            @RequestParam(value = "incidentId", required = false) String incidentId,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        UUID parsedIncidentId = null;
        if (incidentId != null && !incidentId.isBlank()) {
            try {
                parsedIncidentId = UUID.fromString(incidentId);
            } catch (Exception ex) {
                throw new ResponseStatusException(BAD_REQUEST, "incidentId must be UUID");
            }
        }
        return ResponseEntity.ok(alertDispatchService.listAlerts(parsedIncidentId, limit));
    }

    @PostMapping("/api/alerts/test")
    public ResponseEntity<Map<String, Object>> testAlert() {
        UUID incidentId = incidentLookupRepository.findLatestIncidentId();
        if (incidentId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "No incidents found. Create an incident first.");
        }
        AlertMessage testMessage = notificationEventService.fromManualRequest(
                incidentId,
                "HIGH",
                "Notification pipeline test",
                "Manual test alert triggered from /api/alerts/test",
                List.of("Verify channel delivery logs", "Check persisted alert history")
        );
        alertDispatchService.dispatch(testMessage, List.of());
        return ResponseEntity.ok(Map.of(
                "status", "sent",
                "incidentId", testMessage.incidentId().toString(),
                "channels", List.of("webhook", "mock-slack", "email-sink")
        ));
    }

    @PostMapping("/internal/alerts/send")
    public ResponseEntity<Map<String, Object>> sendInternal(@RequestBody AlertSendRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "request body is required");
        }
        UUID incidentId = request.incidentId() == null
                ? incidentLookupRepository.findLatestIncidentId()
                : request.incidentId();
        if (incidentId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "incidentId is required and no incidents exist yet");
        }
        AlertMessage message = notificationEventService.fromManualRequest(
                incidentId,
                request.severity(),
                request.title(),
                request.summary(),
                request.recommendedActions()
        );
        alertDispatchService.dispatch(message, request.channels());
        return ResponseEntity.accepted().body(Map.of(
                "status", "queued",
                "incidentId", message.incidentId().toString(),
                "source", "internal"
        ));
    }

    @PostMapping("/internal/alerts/dlq/replay")
    public ResponseEntity<Map<String, Object>> replayDlq(@RequestBody DlqReplayRequest request) {
        try {
            return ResponseEntity.accepted().body(dlqReplayService.replay(request));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
