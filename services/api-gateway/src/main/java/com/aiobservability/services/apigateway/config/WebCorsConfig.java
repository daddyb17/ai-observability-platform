package com.aiobservability.services.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebCorsConfig implements WebMvcConfigurer {
    private final List<String> allowedOriginPatterns;

    public WebCorsConfig(
            @Value("#{'${app.gateway.cors.allowed-origin-patterns:http://localhost:3000,http://localhost:5173}'.split(',')}")
            List<String> allowedOriginPatterns
    ) {
        this.allowedOriginPatterns = allowedOriginPatterns.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns.toArray(new String[0]))
                .allowedMethods("*")
                .allowedHeaders("*")
                .exposedHeaders("X-Request-Id", "X-Trace-Id");
    }
}
