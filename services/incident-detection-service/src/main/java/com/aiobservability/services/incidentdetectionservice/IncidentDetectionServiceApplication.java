package com.aiobservability.services.incidentdetectionservice;

import com.aiobservability.services.incidentdetectionservice.config.IncidentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(IncidentProperties.class)
public class IncidentDetectionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentDetectionServiceApplication.class, args);
    }
}
