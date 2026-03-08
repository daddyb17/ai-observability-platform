package com.aiobservability.services.logingestionservice.validation;

import com.aiobservability.services.logingestionservice.model.LogIngestionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class LogValidationService {
    private static final Set<String> ALLOWED_LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");

    public void validate(LogIngestionRequest request) {
        List<String> errors = new ArrayList<>();
        requireNotBlank(request.serviceName(), "serviceName", errors);
        requireNotBlank(request.environment(), "environment", errors);
        if (request.timestamp() == null) {
            errors.add("timestamp is required");
        }
        requireNotBlank(request.level(), "level", errors);
        requireNotBlank(request.message(), "message", errors);

        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", errors));
        }

        String normalizedLevel = normalizeLevel(request.level());
        if (!ALLOWED_LEVELS.contains(normalizedLevel)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "level must be one of TRACE, DEBUG, INFO, WARN, ERROR"
            );
        }
    }

    public String normalizeLevel(String level) {
        if (level == null) {
            return null;
        }
        String normalized = level.trim().toUpperCase(Locale.ROOT);
        if ("WARNING".equals(normalized)) {
            return "WARN";
        }
        if ("FATAL".equals(normalized)) {
            return "ERROR";
        }
        return normalized;
    }

    private void requireNotBlank(String value, String fieldName, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(fieldName + " is required");
        }
    }
}
