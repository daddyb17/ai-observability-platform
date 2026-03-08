package com.aiobservability.services.metricsingestionservice;

import com.aiobservability.services.metricsingestionservice.config.MetricsIngestionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MetricsIngestionProperties.class)
public class MetricsIngestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetricsIngestionServiceApplication.class, args);
    }
}
