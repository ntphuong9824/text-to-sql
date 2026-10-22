### Task 7: LLM client OpenAI-compatible

**Files:**
- Create: `llm/LlmClient.java`, `llm/ChatMessage.java`, `llm/OpenAiCompatibleLlmClient.java`, `llm/LlmProperties.java`, `llm/LlmException.java`
- Modify: `application.yml`
- Test: `llm/OpenAiCompatibleLlmClientTest.java` (dùng `MockRestServiceServer`), `src/test/java/com/fsoft/training/support/FakeLlmClient.java`

**Interfaces:**
- Produces:
  - `record ChatMessage(String role, String content)`, với factory `system()`, `user()`, `assistant()`.
  - `interface LlmClient { String complete(List<ChatMessage> messages); }`
  - `class LlmException extends RuntimeException`: `userMessage()` trả câu tiếng Việt, ví dụ "Dịch vụ AI đang quá tải, thử lại sau ít phút" hoặc "Chưa cấu hình LLM_API_KEY".
  - `@ConfigurationProperties("llm") record LlmProperties(String baseUrl, String apiKey, String model, Duration timeout, int maxRetries, double temperature)`. Giá trị mặc định trong yml:
    - `base-url: ${LLM_BASE_URL:https://generativelanguage.googleapis.com/v1beta/openai/}`
    - `api-key: ${LLM_API_KEY:}`
    - `model: ${LLM_MODEL:gemini-2.5-flash}`
    - `timeout: 30s`
    - `max-retries: 3`
    - `temperature: 0`
  - `FakeLlmClient implements LlmClient`: có hàng đợi câu trả lời (`enqueue(String...)`) và ghi lại các request đã nhận (`List<List<ChatMessage>> calls()`). Đăng ký dưới dạng `@TestConfiguration` `@Primary` để các test sau dùng.

Hành vi:
- `POST {baseUrl}chat/completions` với body `{model, messages, temperature}`, header `Authorization: Bearer {apiKey}`. Trả `choices[0].message.content`.
- API key trống → ném `LlmException` ngay, không gọi HTTP. App vẫn phải khởi động được khi không có key.
- Gặp 429 hoặc 5xx → retry tối đa `maxRetries` lần. Thời gian chờ lấy từ header `Retry-After` nếu có, nếu không thì backoff 2s, 4s, 8s. Ghi chú: test chờ 0s qua một `Sleeper` inject được. Hết lượt retry → `LlmException`.
- Timeout hoặc lỗi IO → `LlmException`.

- [ ] **Step 1: Viết test**:
  - Request đúng URL, header và body (model, temperature 0). Trả về nội dung của `choices[0].message.content`.
  - 429, 429, rồi 200 → thành công, có đúng 3 request.
  - Bốn lần 429 liên tiếp → `LlmException`, `userMessage` chứa "quá tải".
  - Key rỗng → `LlmException`, không có request HTTP nào.
  - Server trả 400 → `LlmException`, không retry.
- [ ] **Step 2:** Chạy. Kỳ vọng: FAIL. Implement (dùng `RestClient`). Chạy lại. Kỳ vọng: PASS.
- [ ] **Step 3:** Commit: `feat(llm): add OpenAI-compatible LLM client with retry`.

---

