package com.aiobservability.services.aianalysisservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String provider,
        int timeoutSeconds,
        int maxRetries,
        KafkaProperties kafka,
        OpenAiProperties openai,
        OllamaProperties ollama
) {
    public AiProperties {
        provider = provider == null || provider.isBlank() ? "mock" : provider;
        timeoutSeconds = timeoutSeconds <= 0 ? 20 : timeoutSeconds;
        maxRetries = maxRetries < 0 ? 2 : maxRetries;
        kafka = kafka == null ? new KafkaProperties(null, null, null, 0, null) : kafka;
        openai = openai == null ? new OpenAiProperties(null, null, null) : openai;
        ollama = ollama == null ? new OllamaProperties(null, null) : ollama;
    }

    public record KafkaProperties(
            String topicAnalysisRequest,
            String topicAnalysisResult,
            String deadLetterTopic,
            int sendTimeoutSeconds,
            String consumerGroupId
    ) {
        public KafkaProperties {
            topicAnalysisRequest = isBlank(topicAnalysisRequest) ? "ai.analysis.request" : topicAnalysisRequest;
            topicAnalysisResult = isBlank(topicAnalysisResult) ? "ai.analysis.result" : topicAnalysisResult;
            deadLetterTopic = isBlank(deadLetterTopic) ? "deadletter.ai" : deadLetterTopic;
            sendTimeoutSeconds = sendTimeoutSeconds <= 0 ? 5 : sendTimeoutSeconds;
            consumerGroupId = isBlank(consumerGroupId) ? "ai-analysis-service" : consumerGroupId;
        }
    }

    public record OpenAiProperties(
            String model,
            String apiKey,
            String baseUrl
    ) {
        public OpenAiProperties {
            model = isBlank(model) ? "gpt-4o-mini" : model;
            baseUrl = isBlank(baseUrl) ? "https://api.openai.com/v1" : baseUrl;
        }
    }

    public record OllamaProperties(
            String model,
            String baseUrl
    ) {
        public OllamaProperties {
            model = isBlank(model) ? "llama3" : model;
            baseUrl = isBlank(baseUrl) ? "http://localhost:11434" : baseUrl;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
