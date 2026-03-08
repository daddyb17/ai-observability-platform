package com.aiobservability.services.traceingestionservice;

import com.aiobservability.services.traceingestionservice.config.TraceIngestionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TraceIngestionProperties.class)
public class TraceIngestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TraceIngestionServiceApplication.class, args);
    }
}
