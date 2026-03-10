package com.aiobservability.services.metricsingestionservice.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InternalEndpointSecurityConfig implements WebMvcConfigurer {
    private final InternalEndpointAuthInterceptor internalEndpointAuthInterceptor;

    public InternalEndpointSecurityConfig(InternalEndpointAuthInterceptor internalEndpointAuthInterceptor) {
        this.internalEndpointAuthInterceptor = internalEndpointAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalEndpointAuthInterceptor).addPathPatterns("/internal/**");
    }
}
