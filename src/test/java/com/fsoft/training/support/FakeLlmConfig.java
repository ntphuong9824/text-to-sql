package com.fsoft.training.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class FakeLlmConfig {

    @Bean
    @Primary
    public FakeLlmClient fakeLlmClient() {
        return new FakeLlmClient();
    }
}
