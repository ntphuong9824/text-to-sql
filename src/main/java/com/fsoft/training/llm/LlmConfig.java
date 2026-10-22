package com.fsoft.training.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.http.HttpClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class LlmConfig {

    @Bean
    Sleeper llmSleeper() {
        return duration -> Thread.sleep(duration.toMillis());
    }

    @Bean
    LlmClient llmClient(RestClient.Builder builder, LlmProperties properties, Sleeper sleeper) {
        return create(builder, properties, sleeper);
    }

    static OpenAiCompatibleLlmClient create(RestClient.Builder builder, LlmProperties properties, Sleeper sleeper) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.timeout());
        return new OpenAiCompatibleLlmClient(builder.clone().requestFactory(factory), properties, sleeper);
    }
}
