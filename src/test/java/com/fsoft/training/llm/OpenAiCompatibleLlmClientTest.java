package com.fsoft.training.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleLlmClientTest {

    private static final String URL = "https://llm.example.test/v1/chat/completions";
    private static final String OK = """
            {"choices":[{"message":{"role":"assistant","content":"SELECT 1"}}]}""";

    private final List<Duration> sleeps = new ArrayList<>();
    private MockRestServiceServer server;
    private OpenAiCompatibleLlmClient client;

    @BeforeEach
    void setUp() {
        sleeps.clear();
        client = build("https://llm.example.test/v1/", "secret-key");
    }

    private OpenAiCompatibleLlmClient build(String baseUrl, String key) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new OpenAiCompatibleLlmClient(builder,
                new LlmProperties(baseUrl, key, "test-model", Duration.ofSeconds(5), 3, 0), sleeps::add);
    }

    private static List<ChatMessage> msgs() {
        return List.of(ChatMessage.system("sys"), ChatMessage.user("hi"));
    }

    @Test
    void sendsExpectedRequestAndReturnsContent() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.temperature").value(0.0))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("hi"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        assertThat(client.complete(msgs())).isEqualTo("SELECT 1");
        server.verify();
    }

    @Test
    void baseUrlWithoutTrailingSlashWorks() {
        client = build("https://llm.example.test/v1", "k");
        server.expect(requestTo(URL)).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        assertThat(client.complete(msgs())).isEqualTo("SELECT 1");
        server.verify();
    }

    @Test
    void retriesOn429ThenSucceeds() {
        server.expect(ExpectedCount.times(2), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL)).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        assertThat(client.complete(msgs())).isEqualTo("SELECT 1");
        server.verify();
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    void usesRetryAfterHeaderAndCapsIt() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "7"));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "999"));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY).header("Retry-After", "soon"));
        server.expect(requestTo(URL)).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        client.complete(msgs());
        assertThat(sleeps).containsExactly(Duration.ofSeconds(7), Duration.ofSeconds(30), Duration.ofSeconds(8));
    }

    @Test
    void failsAfterRetriesExhausted() {
        server.expect(ExpectedCount.times(4), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.complete(msgs()))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).userMessage()).contains("quá tải"));
        server.verify();
        assertThat(sleeps).hasSize(3);
    }

    @Test
    void blankKeyFailsWithoutHttp() {
        client = build("https://llm.example.test/v1/", " ");
        assertThatThrownBy(() -> client.complete(msgs()))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).userMessage()).contains("LLM_API_KEY"));
        server.verify(); // no expectations -> no requests
    }

    @Test
    void badRequestIsNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.complete(msgs()))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).userMessage()).isEqualTo("Lỗi khi gọi dịch vụ AI"));
        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void unauthorizedMentionsApiKeyAndNeverLeaksIt() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.complete(msgs()))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> {
                    assertThat(((LlmException) e).userMessage()).contains("API key");
                    assertThat(e.getMessage()).doesNotContain("secret-key");
                });
    }

    @Test
    void ioErrorBecomesNoResponse() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new IOException("timed out");
        });

        assertThatThrownBy(() -> client.complete(msgs()))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).userMessage()).isEqualTo("Dịch vụ AI không phản hồi"));
    }

    @Test
    void emptyChoicesFails() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.complete(msgs())).isInstanceOf(LlmException.class);
    }

    @Test
    void emptyContentFails() {
        server.expect(requestTo(URL)).andRespond(
                withSuccess("{\"choices\":[{\"message\":{\"content\":\"\"}}]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.complete(msgs())).isInstanceOf(LlmException.class);
    }
}
