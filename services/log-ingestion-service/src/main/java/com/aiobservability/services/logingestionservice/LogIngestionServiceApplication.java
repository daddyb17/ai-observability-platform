package com.aiobservability.services.logingestionservice;

import com.aiobservability.services.logingestionservice.config.LogIngestionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LogIngestionProperties.class)
public class LogIngestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogIngestionServiceApplication.class, args);
    }
}
