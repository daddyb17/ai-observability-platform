package com.aiobservability.services.aianalysisservice;

import com.aiobservability.services.aianalysisservice.config.AiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AiProperties.class)
public class AiAnalysisServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAnalysisServiceApplication.class, args);
    }
}
