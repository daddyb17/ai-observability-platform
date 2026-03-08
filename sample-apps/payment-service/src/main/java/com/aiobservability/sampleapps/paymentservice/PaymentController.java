package com.aiobservability.sampleapps.paymentservice;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;

@Validated
@RestController
@RequestMapping("/payments")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final SimulationState simulationState;
    private final Counter timeoutCounter;

    public PaymentController(SimulationState simulationState, MeterRegistry meterRegistry) {
        this.simulationState = simulationState;
        this.timeoutCounter = Counter.builder("payment_timeout_simulation_total")
                .description("How many payment requests failed due to timeout simulation")
                .register(meterRegistry);
    }

    @PostMapping("/charge")
    public ResponseEntity<PaymentChargeResponse> charge(@Valid @RequestBody PaymentChargeRequest request) {
        log.info("payment.charge.request orderId={} amount={} currency={}",
                request.orderId(), request.amount(), request.currency());

        if (simulationState.isLatencyModeEnabled()) {
            sleepSilently(simulationState.getLatencyMs());
            log.warn("payment.charge.latency_mode_applied orderId={} delayMs={}",
                    request.orderId(), simulationState.getLatencyMs());
        }

        if (simulationState.isTimeoutModeEnabled()) {
            timeoutCounter.increment();
            log.error("payment.charge.timeout_mode_triggered orderId={}", request.orderId());
            throw new ResponseStatusException(GATEWAY_TIMEOUT, "simulated payment timeout");
        }

        PaymentChargeResponse response = new PaymentChargeResponse(
                "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "CHARGED"
        );
        log.info("payment.charge.success orderId={} paymentId={}", request.orderId(), response.paymentId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/simulate/timeout")
    public ResponseEntity<Map<String, String>> simulateTimeout() {
        simulationState.setTimeoutModeEnabled(true);
        return ResponseEntity.accepted().body(Map.of("status", "timeout-enabled"));
    }

    @PostMapping("/simulate/latency")
    public ResponseEntity<Map<String, String>> simulateLatency() {
        simulationState.setLatencyModeEnabled(true);
        return ResponseEntity.accepted().body(Map.of("status", "latency-enabled"));
    }

    @PostMapping("/simulate/recover")
    public ResponseEntity<Map<String, String>> recover() {
        simulationState.setTimeoutModeEnabled(false);
        simulationState.setLatencyModeEnabled(false);
        return ResponseEntity.ok(Map.of("status", "recovered"));
    }

    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public record PaymentChargeRequest(
            @NotBlank String orderId,
            @NotNull @DecimalMin(value = "0.01") Double amount,
            @NotBlank String currency
    ) {
    }

    public record PaymentChargeResponse(
            String paymentId,
            String status
    ) {
    }
}
