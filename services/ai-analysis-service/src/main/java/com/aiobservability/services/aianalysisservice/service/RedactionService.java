package com.aiobservability.services.aianalysisservice.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class RedactionService {
    private static final Pattern SECRET_PATTERN = Pattern.compile("(?i)(password|token|api[_-]?key)\\s*[:=]\\s*\\S+");

    public String redact(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return SECRET_PATTERN.matcher(text).replaceAll("$1=REDACTED");
    }
}
