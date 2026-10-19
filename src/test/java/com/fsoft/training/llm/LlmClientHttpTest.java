package com.fsoft.training.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Exercises the production HTTP stack (LlmConfig factory) against a real local server. */
class LlmClientHttpTest {

    private HttpServer server;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OpenAiCompatibleLlmClient client(Duration timeout) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
        return LlmConfig.create(RestClient.builder(),
                new LlmProperties(base, "secret-key", "m", timeout, 0, 0), d -> { });
    }

    private static List<ChatMessage> msgs() {
        return List.of(ChatMessage.user("hi"));
    }

    @Test
    void unauthorizedWithWwwAuthenticateGivesBadKeyMessage() {
        server.createContext("/v1/chat/completions", ex -> {
            ex.getRequestBody().readAllBytes();
            ex.getResponseHeaders().add("WWW-Authenticate", "Bearer");
            ex.sendResponseHeaders(401, -1);
            ex.close();
        });
        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).complete(msgs()))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).userMessage()).contains("API key"));
    }

    @Test
    void okReturnsContent() {
        server.createContext("/v1/chat/completions", ex -> {
            ex.getRequestBody().readAllBytes();
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"SELECT 1\"}}]}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        assertThat(client(Duration.ofSeconds(5)).complete(msgs())).isEqualTo("SELECT 1");
    }

    @Test
    void slowServerTimesOut() {
        server.createContext("/v1/chat/completions", ex -> {
            ex.getRequestBody().readAllBytes();
            try {
                Thread.sleep(2000);
            }
            catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            ex.close();
        });
        assertThatThrownBy(() -> client(Duration.ofMillis(200)).complete(msgs()))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).userMessage()).isEqualTo("Dịch vụ AI không phản hồi"));
    }
}
