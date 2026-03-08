package com.aiobservability.services.logingestionservice.service;

import com.aiobservability.services.logingestionservice.elasticsearch.LogIndexService;
import com.aiobservability.services.logingestionservice.kafka.LogKafkaPublisher;
import com.aiobservability.services.logingestionservice.model.EnrichedLogEvent;
import com.aiobservability.services.logingestionservice.model.LogIngestionRequest;
import com.aiobservability.services.logingestionservice.model.LogIngestionResult;
import com.aiobservability.services.logingestionservice.validation.LogValidationService;
import org.springframework.stereotype.Service;

@Service
public class LogIngestionOrchestrator {
    private final LogValidationService logValidationService;
    private final LogEnrichmentService logEnrichmentService;
    private final LogKafkaPublisher logKafkaPublisher;
    private final LogIndexService logIndexService;

    public LogIngestionOrchestrator(
            LogValidationService logValidationService,
            LogEnrichmentService logEnrichmentService,
            LogKafkaPublisher logKafkaPublisher,
            LogIndexService logIndexService
    ) {
        this.logValidationService = logValidationService;
        this.logEnrichmentService = logEnrichmentService;
        this.logKafkaPublisher = logKafkaPublisher;
        this.logIndexService = logIndexService;
    }

    public LogIngestionResult ingest(LogIngestionRequest request) {
        logValidationService.validate(request);
        EnrichedLogEvent enriched = logEnrichmentService.enrich(request);
        logKafkaPublisher.publish(enriched);
        logIndexService.index(enriched);
        return LogIngestionResult.success(enriched.eventId());
    }
}
