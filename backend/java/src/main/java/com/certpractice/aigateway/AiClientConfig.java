package com.certpractice.aigateway;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AiProperties.class,
        AiQuotaProperties.class,
        AiContextProperties.class,
        AiGenerationProperties.class})
public class AiClientConfig {
}
