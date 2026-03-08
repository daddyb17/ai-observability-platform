package com.aiobservability.services.aianalysisservice.client;

import com.aiobservability.services.aianalysisservice.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AiClientResolver {
    private final Map<String, AiClient> clientsByProvider;
    private final AiProperties properties;

    public AiClientResolver(List<AiClient> clients, AiProperties properties) {
        this.clientsByProvider = clients.stream()
                .collect(Collectors.toMap(
                        client -> client.provider().toLowerCase(Locale.ROOT),
                        Function.identity()
                ));
        this.properties = properties;
    }

    public AiClient resolve() {
        String preferred = properties.provider().toLowerCase(Locale.ROOT);
        AiClient selected = clientsByProvider.get(preferred);
        if (selected != null) {
            return selected;
        }
        AiClient fallback = clientsByProvider.get("mock");
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("No AiClient implementation is registered");
    }

    public Map<String, AiClient> clientsByProvider() {
        return clientsByProvider;
    }
}
