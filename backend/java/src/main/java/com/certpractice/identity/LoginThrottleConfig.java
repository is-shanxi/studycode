package com.certpractice.identity;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LoginThrottleProperties.class)
public class LoginThrottleConfig {
}
