package com.fsoft.training.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fsoft.training.llm.ChatMessage;
import com.fsoft.training.llm.LlmException;
import com.fsoft.training.support.FakeLlmClient;
import com.fsoft.training.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
class ChatApiTest extends PostgresIntegrationTest {

    static final String GOOD_SQL = "SELECT c.region AS region, SUM(f.revenue) AS revenue FROM dw.fact_sales f "
            + "JOIN dw.dim_customer c ON c.customer_key = f.customer_key GROUP BY c.region ORDER BY revenue DESC";

    @Autowired MockMvc mvc;
    @Autowired FakeLlmClient llm;
    private final JsonMapper json = JsonMapper.builder().build();
    private String alice;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        llm.reset();
        JdbcTemplate jdbc = superuserJdbc();
        jdbc.update("delete from app.messages");
        jdbc.update("delete from app.conversations");
        jdbc.update("delete from app.users");
        alice = register("alice");
        bob = register("bob");
    }

    private String register(String username) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password1\"}"))
                .andExpect(status().isCreated()).andReturn();
        return "Bearer " + json.readTree(r.getResponse().getContentAsString()).get("token").asString();
    }

    private MvcResult chat(String token, String conversationId, String question, int expected) throws Exception {
        String idPart = conversationId == null ? "" : "\"conversationId\":\"" + conversationId + "\",";
        return mvc.perform(post("/api/chat").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{" + idPart + "\"question\":\"" + question + "\"}"))
                .andExpect(status().is(expected)).andReturn();
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private String firstChat() throws Exception {
        llm.enqueue(GOOD_SQL);
        return body(chat(alice, null, "doanh thu theo khu vực", 200)).get("conversationId").asString();
    }

    @Test
    void newConversationReturnsResultAndChart() throws Exception {
        llm.enqueue(GOOD_SQL);
        JsonNode res = body(chat(alice, null, "doanh thu theo khu vực", 200));
        assertThat(res.get("conversationId").asString()).isNotBlank();
        assertThat(res.get("title").asString()).isEqualTo("doanh thu theo khu vực");
        JsonNode turn = res.get("turn");
        assertThat(turn.get("turnIndex").asInt()).isZero();
        assertThat(turn.get("status").asString()).isEqualTo("OK");
        assertThat(turn.get("result").get("rows")).hasSize(4);
        assertThat(turn.get("chart").get("type").asString()).isEqualTo("BAR");
        assertThat(turn.get("createdAt").asString())
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
    }

    @Test
    void longTitleIsTruncated() throws Exception {
        llm.enqueue(GOOD_SQL);
        String q = "a".repeat(100);
        JsonNode res = body(chat(alice, null, "  " + q + "  ", 200));
        assertThat(res.get("title").asString()).isEqualTo("a".repeat(60) + "…");
    }

    @Test
    void followUpUsesHistoryAndIsRestoredFromJsonb() throws Exception {
        String id = firstChat();
        llm.reset();
        llm.enqueue("doanh thu tháng 10 theo khu vực", GOOD_SQL);
        JsonNode turn = body(chat(alice, id, "còn tháng 10?", 200)).get("turn");
        assertThat(turn.get("turnIndex").asInt()).isEqualTo(1);
        assertThat(turn.get("rewrittenQuestion").asString()).isEqualTo("doanh thu tháng 10 theo khu vực");
        ChatMessage[] rewrite = llm.calls().get(0).toArray(new ChatMessage[0]);
        StringBuilder all = new StringBuilder();
        for (ChatMessage m : rewrite) {
            all.append(m.content());
        }
        assertThat(all.toString()).contains(GOOD_SQL);

        int callsBefore = llm.calls().size();
        MvcResult r = mvc.perform(get("/api/conversations/" + id).header("Authorization", alice))
                .andExpect(status().isOk()).andReturn();
        JsonNode detail = body(r);
        assertThat(detail.get("turns")).hasSize(2);
        assertThat(detail.get("turns").get(0).get("result").get("rows")).hasSize(4);
        assertThat(detail.get("turns").get(0).get("chart").get("type").asString()).isEqualTo("BAR");
        assertThat(llm.calls()).hasSize(callsBefore);
    }

    @Test
    void listPutsMostRecentlyUsedFirstAndOnlyOwn() throws Exception {
        String first = firstChat();
        llm.enqueue(GOOD_SQL);
        String second = body(chat(alice, null, "doanh thu lần hai", 200)).get("conversationId").asString();
        llm.enqueue(GOOD_SQL);
        chat(bob, null, "của bob", 200);

        JsonNode list = body(mvc.perform(get("/api/conversations").header("Authorization", alice))
                .andExpect(status().isOk()).andReturn());
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("id").asString()).isEqualTo(second);

        llm.enqueue("doanh thu tháng 10 theo khu vực", GOOD_SQL);
        chat(alice, first, "còn tháng 10?", 200);
        list = body(mvc.perform(get("/api/conversations").header("Authorization", alice)).andReturn());
        assertThat(list.get(0).get("id").asString()).isEqualTo(first);
    }

    @Test
    void otherUsersGetIdentical404AndConversationSurvives() throws Exception {
        String id = firstChat();
        llm.reset();
        MvcResult missing = mvc.perform(get("/api/conversations/" + java.util.UUID.randomUUID())
                .header("Authorization", bob)).andExpect(status().isNotFound()).andReturn();
        MvcResult foreign = mvc.perform(get("/api/conversations/" + id).header("Authorization", bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy hội thoại")).andReturn();
        assertThat(foreign.getResponse().getContentAsString()).isEqualTo(missing.getResponse().getContentAsString());
        mvc.perform(delete("/api/conversations/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        chat(bob, id, "xin chào", 404);
        assertThat(llm.calls()).isEmpty();

        mvc.perform(get("/api/conversations/" + id).header("Authorization", alice)).andExpect(status().isOk());
        assertThat(superuserJdbc().queryForObject("select count(*) from app.messages", Integer.class)).isEqualTo(1);
    }

    @Test
    void blockedQuestionIsStored() throws Exception {
        llm.enqueue("DROP TABLE dw.fact_sales");
        JsonNode res = body(chat(alice, null, "xóa bảng", 200));
        assertThat(res.get("turn").get("status").asString()).isEqualTo("BLOCKED");
        JsonNode detail = body(mvc.perform(get("/api/conversations/" + res.get("conversationId").asString())
                .header("Authorization", alice)).andReturn());
        assertThat(detail.get("turns")).hasSize(1);
        assertThat(detail.get("turns").get(0).get("status").asString()).isEqualTo("BLOCKED");
    }

    @Test
    void llmFailureIsErrorTurnNot500() throws Exception {
        llm.enqueueFailure(new LlmException("Dịch vụ AI đang bận"));
        JsonNode turn = body(chat(alice, null, "doanh thu", 200)).get("turn");
        assertThat(turn.get("status").asString()).isEqualTo("ERROR");
        assertThat(turn.get("message").asString()).isEqualTo("Dịch vụ AI đang bận");
    }

    @Test
    void errorTurnsAreNotUsedAsHistory() throws Exception {
        llm.enqueueFailure(new LlmException("Dịch vụ AI đang bận"));
        String id = body(chat(alice, null, "doanh thu", 200)).get("conversationId").asString();
        llm.reset();
        llm.enqueue(GOOD_SQL);
        chat(alice, id, "doanh thu theo khu vực", 200);
        assertThat(llm.calls()).hasSize(1); // no rewrite step, since no OK history
    }

    @Test
    void invalidQuestionsAre400() throws Exception {
        chat(alice, null, "", 400);
        chat(alice, null, "   ", 400);
        chat(alice, null, "a".repeat(501), 400);
        assertThat(llm.calls()).isEmpty();
        llm.enqueue(GOOD_SQL); // the limit is inclusive: 500 characters is accepted
        chat(alice, null, "a".repeat(500), 200);
    }

    @Test
    void deleteCascadesMessages() throws Exception {
        String id = firstChat();
        mvc.perform(delete("/api/conversations/" + id).header("Authorization", alice))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/conversations/" + id).header("Authorization", alice))
                .andExpect(status().isNotFound());
        assertThat(superuserJdbc().queryForObject("select count(*) from app.messages", Integer.class)).isZero();
    }

    @Test
    void malformedConversationIdIs404() throws Exception {
        mvc.perform(get("/api/conversations/abc").header("Authorization", alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy hội thoại"));
        mvc.perform(delete("/api/conversations/abc").header("Authorization", alice))
                .andExpect(status().isNotFound());
    }

    @Test
    void historyReplaysExactlyWhatTheLiveTurnReturned() throws Exception {
        JsonMapper exact = JsonMapper.builder()
                .enable(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
        llm.enqueue("SELECT region AS r, CAST(NULL AS int) AS n, ROUND(SUM(revenue), 2) AS total, "
                + "CAST(100.00 AS numeric(10,2)) AS hundred FROM dw.fact_sales f "
                + "JOIN dw.dim_customer c ON c.customer_key = f.customer_key GROUP BY region");
        MvcResult live = chat(alice, null, "tổng doanh thu", 200);
        JsonNode liveRes = exact.readTree(live.getResponse().getContentAsString());
        assertThat(liveRes.get("turn").get("status").asString()).isEqualTo("OK");
        assertThat(live.getResponse().getContentAsString()).contains("100.00");
        String id = liveRes.get("conversationId").asString();
        JsonNode hist = exact.readTree(mvc.perform(get("/api/conversations/" + id).header("Authorization", alice))
                .andReturn().getResponse().getContentAsString());
        assertThat(hist.get("turns").get(0)).isEqualTo(liveRes.get("turn"));
        assertThat(hist.get("turns").get(0).get("result").toString())
                .isEqualTo(liveRes.get("turn").get("result").toString()).contains("100.00");
        assertThat(hist.get("turns").get(0).get("result").get("truncated").asBoolean()).isFalse();
    }

    @Test
    void conversationDeletedWhilePipelineRunsIs404() throws Exception {
        String id = firstChat();
        llm.reset();
        llm.onComplete(() -> superuserJdbc().update("delete from app.conversations"));
        llm.enqueue("doanh thu tháng 10 theo khu vực", GOOD_SQL);
        chat(alice, id, "còn tháng 10?", 404);
        assertThat(superuserJdbc().queryForObject("select count(*) from app.messages", Integer.class)).isZero();
    }

    @Test
    void duplicateTurnIndexIsRejectedByDatabase() throws Exception {
        String id = firstChat();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> superuserJdbc().update(
                "insert into app.messages(conversation_id, turn_index, question, status) values (?::uuid, 0, 'x', 'OK')", id))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }
}
