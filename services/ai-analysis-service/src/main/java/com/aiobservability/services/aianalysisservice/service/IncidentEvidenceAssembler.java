package com.aiobservability.services.aianalysisservice.service;

import com.aiobservability.services.aianalysisservice.model.IncidentContext;
import com.aiobservability.services.aianalysisservice.model.IncidentSignalRecord;
import com.aiobservability.services.aianalysisservice.model.PromptEvidence;
import com.aiobservability.services.aianalysisservice.model.TraceSummaryRecord;
import com.aiobservability.services.aianalysisservice.repository.IncidentContextRepository;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class IncidentEvidenceAssembler {
    private final IncidentContextRepository contextRepository;
    private final RedactionService redactionService;
    private final DecimalFormat decimalFormat = new DecimalFormat("0.###");

    public IncidentEvidenceAssembler(
            IncidentContextRepository contextRepository,
            RedactionService redactionService
    ) {
        this.contextRepository = contextRepository;
        this.redactionService = redactionService;
    }

    public PromptEvidence assemble(IncidentContext incident) {
        List<IncidentSignalRecord> signals = contextRepository.findSignals(incident.id(), 200);
        List<PromptEvidence.TopError> topErrors = aggregateTopErrors(signals);
        List<PromptEvidence.MetricBreach> metricBreaches = collectMetricBreaches(signals);
        TraceSummaryRecord traceSummary = contextRepository.findTraceSummary(incident.rootTraceId());
        List<String> evidenceLines = buildEvidenceLines(topErrors, metricBreaches, traceSummary);
        return new PromptEvidence(topErrors, metricBreaches, traceSummary, evidenceLines);
    }

    private List<PromptEvidence.TopError> aggregateTopErrors(List<IncidentSignalRecord> signals) {
        Map<String, Long> countsByMessage = new LinkedHashMap<>();
        for (IncidentSignalRecord signal : signals) {
            Map<String, Object> payload = signal.signalPayload();
            String message = firstNonBlank(
                    asText(payload.get("message")),
                    asText(payload.get("exceptionType")),
                    signal.signalKey()
            );
            if (message == null || message.isBlank()) {
                continue;
            }
            String normalized = redactionService.redact(message);
            countsByMessage.put(normalized, countsByMessage.getOrDefault(normalized, 0L) + 1L);
        }
        return countsByMessage.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .map(entry -> new PromptEvidence.TopError(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<PromptEvidence.MetricBreach> collectMetricBreaches(List<IncidentSignalRecord> signals) {
        List<PromptEvidence.MetricBreach> breaches = new ArrayList<>();
        for (IncidentSignalRecord signal : signals) {
            if (!signal.signalType().toUpperCase(Locale.ROOT).contains("METRIC")) {
                continue;
            }
            Map<String, Object> payload = signal.signalPayload();
            String metricName = firstNonBlank(asText(payload.get("metricName")), signal.signalKey());
            Double value = asDouble(payload.get("value"));
            Double threshold = asDouble(payload.get("threshold"));
            if (metricName == null || value == null || threshold == null) {
                continue;
            }
            breaches.add(new PromptEvidence.MetricBreach(metricName, value, threshold));
        }
        return breaches.stream().limit(5).toList();
    }

    private List<String> buildEvidenceLines(
            List<PromptEvidence.TopError> topErrors,
            List<PromptEvidence.MetricBreach> metricBreaches,
            TraceSummaryRecord traceSummary
    ) {
        List<String> evidence = new ArrayList<>();
        for (PromptEvidence.TopError topError : topErrors) {
            evidence.add(topError.count() + " occurrences of \"" + topError.message() + "\"");
        }
        for (PromptEvidence.MetricBreach breach : metricBreaches) {
            evidence.add(
                    breach.metric() + " breached threshold: "
                            + decimalFormat.format(breach.value())
                            + " > "
                            + decimalFormat.format(breach.threshold())
            );
        }
        if (traceSummary != null) {
            evidence.add("Trace bottleneck: " + firstNonBlank(traceSummary.bottleneckService(), "unknown-service"));
            evidence.add("Trace duration(ms): " + traceSummary.durationMs());
        }
        return evidence;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
