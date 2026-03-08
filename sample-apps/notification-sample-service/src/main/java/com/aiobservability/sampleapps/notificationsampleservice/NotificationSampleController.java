package com.aiobservability.sampleapps.notificationsampleservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationSampleController {
    private static final Logger log = LoggerFactory.getLogger(NotificationSampleController.class);

    @PostMapping("/send")
    public ResponseEntity<NotificationResponse> send(@Valid @RequestBody NotificationRequest request) {
        String notificationId = "NTF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("notification.send.success orderId={} recipient={} notificationId={}",
                request.orderId(), request.recipient(), notificationId);
        return ResponseEntity.ok(new NotificationResponse(notificationId, "SENT"));
    }

    public record NotificationRequest(
            @NotBlank String orderId,
            String recipient,
            @NotBlank String eventType
    ) {
    }

    public record NotificationResponse(
            String notificationId,
            String status
    ) {
    }
}
