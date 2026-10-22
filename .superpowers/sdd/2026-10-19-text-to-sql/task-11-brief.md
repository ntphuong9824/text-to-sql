### Task 11: Hội thoại, chat API, lịch sử

**Files:**
- Create: `conversation/ConversationRepository.java`, `conversation/MessageRepository.java`, `conversation/ConversationService.java`, `conversation/ChatController.java`, `conversation/ConversationController.java`, `conversation/ConversationDtos.java`
- Test: `conversation/ChatApiTest.java` (integration, MockMvc + `FakeLlmClient`)

**Interfaces:**
- Consumes: `Nl2SqlPipeline.run`, `TurnContext`, `TurnOutcome` (Task 9). `CurrentUser.id` (Task 10).
- Produces:
  - DTO:
    - `ChatRequest(UUID conversationId /*nullable*/, @NotBlank @Size(max=500) String question)`
    - `TurnDto(int turnIndex, String question, String rewrittenQuestion, String sql, TurnStatus status, String message, QueryResult result, ChartSpec chart, Instant createdAt)`
    - `ChatResponse(UUID conversationId, String title, TurnDto turn)`
    - `ConversationSummary(UUID id, String title, Instant updatedAt)`
    - `ConversationDetail(UUID id, String title, List<TurnDto> turns)`
  - `ConversationService`:
    - `ChatResponse ask(long userId, UUID conversationIdOrNull, String question)`
    - `List<ConversationSummary> list(long userId)`
    - `ConversationDetail get(long userId, UUID id)`
    - `void delete(long userId, UUID id)`
    - Không tìm thấy hoặc không thuộc user → `ConversationNotFoundException` → 404.
  - Endpoint:
    - `POST /api/chat`
    - `GET /api/conversations`
    - `GET /api/conversations/{id}`
    - `DELETE /api/conversations/{id}` → 204

Hành vi của `ask`:
1. `conversationId` null → tạo hội thoại mới, title = câu hỏi cắt còn 60 ký tự (cắt thì thêm "…").
2. Lấy 3 lượt gần nhất **có status OK** làm `TurnContext`.
3. Chạy pipeline.
4. Lưu message, kể cả khi BLOCKED hoặc ERROR, rồi cập nhật `updated_at`.
5. Ghi `result` và `chart` dưới dạng JSONB, serialize bằng `JsonMapper`.

Lưu ý: không giữ transaction DB mở trong lúc chờ LLM. Pipeline chạy ngoài transaction, chỉ thao tác lưu mới nằm trong transaction.

- [ ] **Step 1: Viết test** `ChatApiTest`:
  - Alice gửi `POST /api/chat` không kèm id → 200, có `conversationId` mới, `turn.status=OK`, `result.rows` có 4 dòng, `chart.type=BAR`.
  - Alice gửi tiếp câu nối tiếp với cùng id (Fake trả câu đã viết lại rồi SQL) → `turnIndex=1`. Request gửi tới LLM để viết lại phải chứa SQL của lượt 0.
  - `GET /api/conversations/{id}` → có 2 lượt, `result` khôi phục được từ JSONB (4 dòng). `FakeLlmClient` không nhận thêm request nào.
  - `GET /api/conversations` → hội thoại vừa dùng đứng đầu danh sách.
  - Bob dùng token của mình gọi `GET`, `DELETE` hoặc `POST /api/chat` với id của Alice → 404. Hội thoại của Alice vẫn còn.
  - Câu hỏi bị chặn (Fake trả `DROP ...`) → 200, `status=BLOCKED`, vẫn được lưu vào lịch sử.
  - `LlmException` → 200, `status=ERROR`, message tiếng Việt, không phải 500.
  - Câu hỏi rỗng → 400. Câu hỏi 501 ký tự → 400.
  - Alice xóa hội thoại → 204. GET sau đó → 404, và các message trong DB cũng bị xóa (cascade).
- [ ] **Step 2:** Chạy. Kỳ vọng: FAIL. Implement. Chạy lại. Kỳ vọng: PASS.
- [ ] **Step 3:** Chạy toàn bộ `./mvnw test`. Kỳ vọng: tất cả PASS.
- [ ] **Step 4:** Commit: `feat(chat): add persistent conversations and chat API`.

---

