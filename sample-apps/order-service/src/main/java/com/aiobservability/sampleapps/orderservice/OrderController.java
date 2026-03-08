package com.aiobservability.sampleapps.orderservice;

import com.aiobservability.sampleapps.orderservice.model.CreateOrderRequest;
import com.aiobservability.sampleapps.orderservice.model.LoadTestRequest;
import com.aiobservability.sampleapps.orderservice.model.OrderRecord;
import com.aiobservability.sampleapps.orderservice.service.OrderWorkflowService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping
public class OrderController {
    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderWorkflowService orderWorkflowService;
    private final Counter orderCreateCounter;

    public OrderController(OrderWorkflowService orderWorkflowService, MeterRegistry meterRegistry) {
        this.orderWorkflowService = orderWorkflowService;
        this.orderCreateCounter = Counter.builder("order_created_total")
                .description("Number of orders successfully created")
                .register(meterRegistry);
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderRecord> createOrder(@Valid @RequestBody CreateOrderRequest body) {
        OrderRecord record = orderWorkflowService.createOrder(body);
        orderCreateCounter.increment();
        return ResponseEntity.ok(record);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<OrderRecord> getOrder(@PathVariable String id) {
        return ResponseEntity.ok(orderWorkflowService.findOrder(id));
    }

    @PostMapping("/orders/load-test")
    public ResponseEntity<Map<String, Object>> loadTest(@RequestBody(required = false) LoadTestRequest request) {
        int count = request == null ? 20 : request.count();
        for (int i = 0; i < count; i++) {
            try {
                orderWorkflowService.createOrder(new CreateOrderRequest(
                        new BigDecimal("100.00"),
                        "USD",
                        "load-test@example.com"
                ));
            } catch (Exception ex) {
                log.warn("order.load_test.iteration_failed iteration={} error={}", i + 1, ex.getMessage());
            }
        }
        return ResponseEntity.accepted().body(Map.of("status", "load-test-complete", "requested", count));
    }
}
