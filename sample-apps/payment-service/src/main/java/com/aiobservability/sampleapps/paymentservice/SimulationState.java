package com.aiobservability.sampleapps.paymentservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SimulationState {
    private final AtomicBoolean timeoutModeEnabled = new AtomicBoolean(false);
    private final AtomicBoolean latencyModeEnabled = new AtomicBoolean(false);
    private final long latencyMs;

    public SimulationState(@Value("${app.simulation.latency-ms:2500}") long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public boolean isTimeoutModeEnabled() {
        return timeoutModeEnabled.get();
    }

    public void setTimeoutModeEnabled(boolean enabled) {
        timeoutModeEnabled.set(enabled);
    }

    public boolean isLatencyModeEnabled() {
        return latencyModeEnabled.get();
    }

    public void setLatencyModeEnabled(boolean enabled) {
        latencyModeEnabled.set(enabled);
    }

    public long getLatencyMs() {
        return latencyMs;
    }
}
