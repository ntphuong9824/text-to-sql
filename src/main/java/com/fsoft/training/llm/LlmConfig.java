package com.fsoft.training.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class LlmConfig {

    @Bean
    Sleeper llmSleeper() {
        return duration -> Thread.sleep(duration.toMillis());
    }

    @Bean
    LlmClient llmClient(RestClient.Builder builder, LlmProperties properties, Sleeper sleeper) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        RestClient.Builder configured = builder.clone().requestFactory(factory);
        return new OpenAiCompatibleLlmClient(configured, properties, sleeper);
    }
}
