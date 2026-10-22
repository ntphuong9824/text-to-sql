package com.fsoft.training.llm;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("llm")
public record LlmProperties(String baseUrl, String apiKey, String model, Duration timeout, int maxRetries,
        double temperature) {

    /** Omits the API key so it can never leak through logging. */
    @Override
    public String toString() {
        return "LlmProperties[baseUrl=" + baseUrl + ", model=" + model + ", timeout=" + timeout
                + ", maxRetries=" + maxRetries + ", temperature=" + temperature + "]";
    }
}
