package com.aiobservability.services.aianalysisservice;

import com.aiobservability.shared.models.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        LOGGER.warn("Request failed with status {}: {}", exception.getStatusCode(), exception.getReason());
        ApiErrorResponse body = new ApiErrorResponse(
                String.valueOf(exception.getStatusCode().value()),
                exception.getReason() == null ? "Request failed" : exception.getReason(),
                Instant.now()
        );
        return ResponseEntity.status(exception.getStatusCode()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception exception) {
        LOGGER.error("Unhandled exception", exception);
        ApiErrorResponse body = new ApiErrorResponse(
                "500",
                "Internal server error",
                Instant.now()
        );
        return ResponseEntity.internalServerError().body(body);
    }
}
