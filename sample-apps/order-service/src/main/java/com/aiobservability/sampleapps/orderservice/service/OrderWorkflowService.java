package com.aiobservability.sampleapps.orderservice.service;

import com.aiobservability.sampleapps.orderservice.model.CreateOrderRequest;
import com.aiobservability.sampleapps.orderservice.model.OrderRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(OrderWorkflowService.class);

    private final RestClient paymentClient;
    private final RestClient notificationClient;
    private final Map<String, OrderRecord> orders = new ConcurrentHashMap<>();

    public OrderWorkflowService(
            @Qualifier("paymentClient") RestClient paymentClient,
            @Qualifier("notificationClient") RestClient notificationClient
    ) {
        this.paymentClient = paymentClient;
        this.notificationClient = notificationClient;
    }

    public OrderRecord createOrder(CreateOrderRequest request) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("order.create.start orderId={} amount={} currency={}", orderId, request.amount(), request.currency());

        PaymentChargeResponse paymentResponse;
        try {
            paymentResponse = paymentClient.post()
                    .uri("/payments/charge")
                    .body(new PaymentChargeRequest(orderId, request.amount().doubleValue(), request.currency()))
                    .retrieve()
                    .body(PaymentChargeResponse.class);
        } catch (RestClientException ex) {
            log.error("order.create.payment_failed orderId={} error={}", orderId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "payment-service unavailable", ex);
        }

        NotificationResponse notificationResponse;
        try {
            notificationResponse = notificationClient.post()
                    .uri("/notifications/send")
                    .body(new NotificationRequest(orderId, request.customerEmail(), "ORDER_CREATED"))
                    .retrieve()
                    .body(NotificationResponse.class);
        } catch (RestClientException ex) {
            log.error("order.create.notification_failed orderId={} error={}", orderId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "notification-sample-service unavailable", ex);
        }

        OrderRecord record = new OrderRecord(
                orderId,
                request.amount(),
                request.currency(),
                "CREATED",
                paymentResponse == null ? null : paymentResponse.paymentId(),
                notificationResponse == null ? null : notificationResponse.notificationId(),
                Instant.now()
        );
        orders.put(orderId, record);
        log.info("order.create.success orderId={} paymentId={} notificationId={}",
                record.orderId(), record.paymentId(), record.notificationId());
        return record;
    }

    public OrderRecord findOrder(String orderId) {
        OrderRecord order = orders.get(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
        }
        return order;
    }

    private record PaymentChargeRequest(String orderId, double amount, String currency) {
    }

    private record PaymentChargeResponse(String paymentId, String status) {
    }

    private record NotificationRequest(String orderId, String recipient, String eventType) {
    }

    private record NotificationResponse(String notificationId, String status) {
    }
}
