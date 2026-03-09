package com.aiobservability.services.aianalysisservice.service;

import com.aiobservability.services.aianalysisservice.client.AiClient;
import com.aiobservability.services.aianalysisservice.client.AiClientResolver;
import com.aiobservability.services.aianalysisservice.config.AiProperties;
import com.aiobservability.services.aianalysisservice.kafka.AiAnalysisResultPublisher;
import com.aiobservability.services.aianalysisservice.model.AiAnalysisRecord;
import com.aiobservability.services.aianalysisservice.model.AiAnalysisResult;
import com.aiobservability.services.aianalysisservice.model.IncidentAnalysisPrompt;
import com.aiobservability.services.aianalysisservice.model.IncidentContext;
import com.aiobservability.services.aianalysisservice.model.PromptEvidence;
import com.aiobservability.services.aianalysisservice.repository.IncidentAnalysisRepository;
import com.aiobservability.services.aianalysisservice.repository.IncidentContextRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AiAnalysisService {
    private final IncidentContextRepository contextRepository;
    private final IncidentAnalysisRepository analysisRepository;
    private final IncidentEvidenceAssembler evidenceAssembler;
    private final PromptBuilder promptBuilder;
    private final AiClientResolver clientResolver;
    private final AiAnalysisResultPublisher resultPublisher;
    private final AiProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public AiAnalysisService(
            IncidentContextRepository contextRepository,
            IncidentAnalysisRepository analysisRepository,
            IncidentEvidenceAssembler evidenceAssembler,
            PromptBuilder promptBuilder,
            AiClientResolver clientResolver,
            AiAnalysisResultPublisher resultPublisher,
            AiProperties properties
    ) {
        this.contextRepository = contextRepository;
        this.analysisRepository = analysisRepository;
        this.evidenceAssembler = evidenceAssembler;
        this.promptBuilder = promptBuilder;
        this.clientResolver = clientResolver;
        this.resultPublisher = resultPublisher;
        this.properties = properties;
        this.circuitBreaker = CircuitBreaker.of(
                "aiProvider",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50.0f)
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .waitDurationInOpenState(Duration.ofSeconds(20))
                        .build()
        );
        this.retry = Retry.of(
                "aiProvider",
                RetryConfig.custom()
                        .maxAttempts(Math.max(1, properties.maxRetries() + 1))
                        .waitDuration(Duration.ofMillis(250))
                        .retryExceptions(RuntimeException.class)
                        .build()
        );
    }

    public AiAnalysisRecord analyzeIncident(UUID incidentId, String triggerSource) {
        IncidentContext incident = contextRepository.findIncident(incidentId);
        if (incident == null) {
            throw new ResponseStatusException(NOT_FOUND, "Incident not found: " + incidentId);
        }

        PromptEvidence evidence = evidenceAssembler.assemble(incident);
        IncidentAnalysisPrompt prompt = promptBuilder.build(incident, evidence);
        AiAnalysisResult analysisResult = analyzeWithFallback(prompt, incident, triggerSource);
        AiAnalysisRecord saved = analysisRepository.save(incident.id(), analysisResult);
        resultPublisher.publish(saved);
        return saved;
    }

    public AiAnalysisRecord getLatestAnalysis(UUID incidentId) {
        return analysisRepository.findLatestByIncident(incidentId);
    }

    public List<Map<String, Object>> getProviderStatus() {
        String selected = properties.provider().toLowerCase(Locale.ROOT);
        return clientResolver.clientsByProvider().values().stream()
                .map(client -> {
                    Map<String, Object> status = new LinkedHashMap<>();
                    status.put("provider", client.provider());
                    status.put("modelName", client.modelName());
                    status.put("selected", selected.equals(client.provider().toLowerCase(Locale.ROOT)));
                    return status;
                })
                .toList();
    }

    private AiAnalysisResult analyzeWithFallback(
            IncidentAnalysisPrompt prompt,
            IncidentContext incident,
            String triggerSource
    ) {
        AiClient client = clientResolver.resolve();
        Exception lastFailure;
        Supplier<AiAnalysisResult> operation = Retry.decorateSupplier(
                retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> runWithTimeoutUnchecked(client, prompt))
        );
        try {
            AiAnalysisResult result = operation.get();
            if (result != null) {
                return result;
            }
            lastFailure = new IllegalStateException("AI client returned null response");
        } catch (CallNotPermittedException ex) {
            lastFailure = ex;
        } catch (Exception ex) {
            lastFailure = unwrap(ex);
        }
        return deterministicFallback(prompt, incident, triggerSource, client, lastFailure);
    }

    private AiAnalysisResult runWithTimeout(AiClient client, IncidentAnalysisPrompt prompt)
            throws TimeoutException, ExecutionException, InterruptedException {
        CompletableFuture<AiAnalysisResult> future = CompletableFuture.supplyAsync(() -> client.analyze(prompt));
        return future.get(properties.timeoutSeconds(), TimeUnit.SECONDS);
    }

    private AiAnalysisResult runWithTimeoutUnchecked(AiClient client, IncidentAnalysisPrompt prompt) {
        try {
            return runWithTimeout(client, prompt);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private AiAnalysisResult deterministicFallback(
            IncidentAnalysisPrompt prompt,
            IncidentContext incident,
            String triggerSource,
            AiClient attemptedClient,
            Exception failure
    ) {
        String primaryService = prompt.affectedServices().isEmpty()
                ? "unknown-service"
                : prompt.affectedServices().get(0);
        String summary = primaryService
                + " is showing high error rate and repeated failures. Review availability and connection behavior.";
        String rootCause = "AI provider unavailable or timed out. Use correlated logs, metrics, and traces for confirmation.";
        List<String> recommendations = List.of(
                "Inspect recent deployment/configuration changes in " + primaryService,
                "Check dependency health and connection pool utilization",
                "Correlate failures with trace bottleneck spans"
        );

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("fallback", true);
        raw.put("incidentId", incident.id());
        raw.put("triggerSource", triggerSource);
        raw.put("attemptedProvider", attemptedClient.provider());
        raw.put("attemptedModel", attemptedClient.modelName());
        if (failure != null) {
            raw.put("errorType", failure.getClass().getSimpleName());
            raw.put("errorMessage", failure.getMessage());
        }

        return new AiAnalysisResult(
                summary,
                rootCause,
                0.55,
                recommendations,
                prompt.evidence(),
                "fallback",
                attemptedClient.modelName(),
                raw,
                true
        );
    }

    private Exception unwrap(Exception exception) {
        if (exception.getCause() instanceof Exception cause) {
            return cause;
        }
        return exception;
    }
}
