package com.fsoft.training.llm;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);
    private static final Duration MAX_WAIT = Duration.ofSeconds(30);
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(2);

    static final String MSG_OVERLOADED = "Dịch vụ AI đang quá tải, thử lại sau ít phút";
    static final String MSG_NO_KEY = "Chưa cấu hình LLM_API_KEY";
    static final String MSG_BAD_KEY = "API key của dịch vụ AI không hợp lệ hoặc không được phép";
    static final String MSG_GENERIC = "Lỗi khi gọi dịch vụ AI";
    static final String MSG_TIMEOUT = "Dịch vụ AI không phản hồi";

    private final RestClient restClient;
    private final LlmProperties properties;
    private final Sleeper sleeper;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final String url;

    public OpenAiCompatibleLlmClient(RestClient.Builder builder, LlmProperties properties, Sleeper sleeper) {
        this.restClient = builder.build();
        this.properties = properties;
        this.sleeper = sleeper;
        String base = properties.baseUrl() == null ? "" : properties.baseUrl().trim();
        this.url = (base.endsWith("/") ? base : base + "/") + "chat/completions";
    }

    @Override
    public String complete(List<ChatMessage> messages) {
        String key = properties.apiKey();
        if (key == null || key.isBlank()) {
            throw new LlmException(MSG_NO_KEY);
        }
        String body = buildBody(messages);
        int attempt = 0;
        while (true) {
            try {
                String response = restClient.post()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                return extractContent(response);
            }
            catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status == 429 || status >= 500) {
                    if (attempt >= properties.maxRetries()) {
                        log.warn("LLM call failed with status {} after {} retries", status, attempt);
                        throw new LlmException(MSG_OVERLOADED, e);
                    }
                    Duration wait = waitFor(e.getResponseHeaders(), attempt);
                    attempt++;
                    log.warn("LLM call returned {}, retry {}/{} in {}s", status, attempt,
                            properties.maxRetries(), wait.toSeconds());
                    pause(wait);
                    continue;
                }
                log.warn("LLM call failed with status {}", status);
                if (status == 401 || status == 403) {
                    throw new LlmException(MSG_BAD_KEY, e);
                }
                throw new LlmException(MSG_GENERIC, e);
            }
            catch (ResourceAccessException e) {
                log.warn("LLM call I/O failure: {}", e.getClass().getSimpleName());
                throw new LlmException(MSG_TIMEOUT, e);
            }
            catch (RestClientException e) {
                log.warn("LLM call failed: {}", e.getClass().getSimpleName());
                throw new LlmException(MSG_GENERIC, e);
            }
        }
    }

    private String buildBody(List<ChatMessage> messages) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.model());
        payload.put("messages", messages);
        payload.put("temperature", properties.temperature());
        return mapper.writeValueAsString(payload);
    }

    private String extractContent(String response) {
        try {
            JsonNode content = mapper.readTree(response).path("choices").path(0).path("message").path("content");
            if (content.isString() && !content.asString().isBlank()) {
                return content.asString();
            }
        }
        catch (RuntimeException e) {
            throw new LlmException(MSG_GENERIC, e);
        }
        throw new LlmException(MSG_GENERIC);
    }

    private Duration waitFor(HttpHeaders headers, int attempt) {
        Duration wait = BASE_BACKOFF.multipliedBy(1L << Math.min(attempt, 10));
        String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                long seconds = Long.parseLong(retryAfter.trim());
                if (seconds >= 0) {
                    wait = Duration.ofSeconds(seconds);
                }
            }
            catch (NumberFormatException ignored) {
                // fall back to exponential backoff
            }
        }
        return wait.compareTo(MAX_WAIT) > 0 ? MAX_WAIT : wait;
    }

    private void pause(Duration wait) {
        try {
            sleeper.sleep(wait);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(MSG_TIMEOUT, e);
        }
    }
}
