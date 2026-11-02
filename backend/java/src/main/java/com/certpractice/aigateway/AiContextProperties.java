package com.certpractice.aigateway;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI context minimization policy. Official answers and explanations stay out of
 * the model context unless an administrator explicitly enables them.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai.context")
public class AiContextProperties {

    private boolean sendOfficialAnswer;
}
