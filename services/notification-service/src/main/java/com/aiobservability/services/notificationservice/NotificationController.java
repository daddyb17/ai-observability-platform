package com.aiobservability.services.notificationservice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
public class NotificationController {

    @GetMapping("/api/alerts")
    public ResponseEntity<Map<String, Object>> listAlerts() {
        return ResponseEntity.ok(Map.of("items", java.util.List.of()));
    }

    @PostMapping("/api/alerts/test")
    public ResponseEntity<Map<String, String>> testAlert() {
        return ResponseEntity.ok(Map.of("status", "sent"));
    }
}
