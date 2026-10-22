package com.fsoft.training.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

class LlmContextTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LlmProperties.class)
    static class Props {
        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }

    @Test
    void realClientBeanLoadsWithoutApiKey() {
        new ApplicationContextRunner()
                .withUserConfiguration(Props.class, LlmConfig.class)
                .withPropertyValues("llm.base-url=https://example.test/v1/", "llm.api-key=", "llm.model=m",
                        "llm.timeout=30s", "llm.max-retries=3", "llm.temperature=0")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(OpenAiCompatibleLlmClient.class);
                });
    }
}
