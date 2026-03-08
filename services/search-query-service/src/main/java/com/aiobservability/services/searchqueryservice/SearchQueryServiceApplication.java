package com.aiobservability.services.searchqueryservice;

import com.aiobservability.services.searchqueryservice.config.SearchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SearchProperties.class)
public class SearchQueryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchQueryServiceApplication.class, args);
    }
}
