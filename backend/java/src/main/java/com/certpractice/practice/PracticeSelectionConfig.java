package com.certpractice.practice;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PracticeSelectionProperties.class)
public class PracticeSelectionConfig {
}
