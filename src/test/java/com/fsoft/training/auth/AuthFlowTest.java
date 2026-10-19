package com.fsoft.training.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fsoft.training.support.PostgresIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AuthFlowTest extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @Autowired JdbcTemplate appJdbc;
    @Autowired @Qualifier("analyst") JdbcTemplate analyst;

    private static String body(String u, String p) {
        return "{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}";
    }

    private void register(String u, String p, int expected) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(u, p)))
                .andExpect(status().is(expected));
    }

    @Test
    void registerThenDuplicateIs409() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("alice", "password1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("alice"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("alice", "password1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập đã tồn tại"));
    }

    @Test
    void loginSuccessAndFailures() throws Exception {
        register("bob", "password1", 201);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body("bob", "password1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body("bob", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sai tên đăng nhập hoặc mật khẩu"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body("nobody", "password1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sai tên đăng nhập hoặc mật khẩu"));
    }

    @Test
    void shortPasswordIs400() throws Exception {
        register("carol", "12345", 400);
    }

    @Test
    void invalidUsernameAndMalformedJsonAre400() throws Exception {
        register("a b", "password1", 400);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bcryptByteLimitIsEnforced() throws Exception {
        register("dave", "a".repeat(72), 201);
        register("dave2", "a".repeat(73), 400);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body("dave", "a".repeat(100))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRejectsMissingBadAndExpiredTokens() throws Exception {
        mvc.perform(get("/api/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập"));

        String valid = token(Duration.ofHours(1));
        String tampered = valid.substring(0, valid.length() - 4) + (valid.endsWith("AAAA") ? "BBBB" : "AAAA");
        mvc.perform(get("/api/conversations").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/conversations").header("Authorization", "Bearer " + token(Duration.ofMinutes(-10))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenPassesSecurity() throws Exception {
        // No /api/conversations controller yet: anything but 401 proves Security let the request through.
        int status = mvc.perform(get("/api/conversations").header("Authorization", "Bearer " + token(Duration.ofHours(1))))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(401);
    }

    @Test
    void passwordIsStoredAsBcrypt() throws Exception {
        register("erin", "s3cret-pass", 201);
        String hash = appJdbc.queryForObject("select password_hash from app.users where username = 'erin'", String.class);
        assertThat(hash).startsWith("$2").doesNotContain("s3cret-pass");
    }

    @Test
    void analystCannotReadAppUsers() {
        assertThatThrownBy(() -> analyst.queryForList("select * from app.users")).isInstanceOf(DataAccessException.class);
    }

    private String token(Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("1").claim("username", "x")
                .issuedAt(now.minus(Duration.ofDays(1))).expiresAt(now.plus(ttl)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
