package com.aiobservability.sampleapps.orderservice.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OrderClientConfig {

    @Bean
    @Qualifier("paymentClient")
    public RestClient paymentClient(
            RestClient.Builder builder,
            @Value("${app.downstream.payment-base-url}") String paymentBaseUrl
    ) {
        return builder.baseUrl(paymentBaseUrl).build();
    }

    @Bean
    @Qualifier("notificationClient")
    public RestClient notificationClient(
            RestClient.Builder builder,
            @Value("${app.downstream.notification-base-url}") String notificationBaseUrl
    ) {
        return builder.baseUrl(notificationBaseUrl).build();
    }
}
