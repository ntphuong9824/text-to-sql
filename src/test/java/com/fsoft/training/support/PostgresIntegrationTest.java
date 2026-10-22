package com.fsoft.training.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base class for integration tests. One Postgres container (with db/init scripts applied)
 * is shared by every subclass in the JVM.
 */
@SpringBootTest
@Import(FakeLlmConfig.class)
public abstract class PostgresIntegrationTest {

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6")
            .withDatabaseName("textsql")
            .withUsername("postgres")
            .withPassword("postgres")
            .withCopyFileToContainer(MountableFile.forHostPath("db/init"), "/docker-entrypoint-initdb.d/");

    public static final String TEST_JWT_SECRET = "test-only-jwt-secret-at-least-32-bytes-long!!";

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("app.datasource.app.url", POSTGRES::getJdbcUrl);
        registry.add("app.datasource.analyst.url", POSTGRES::getJdbcUrl);
        registry.add("app.jwt.secret", () -> TEST_JWT_SECRET);
        // application.yml imports a local .env; never let tests see a real key or reach the real endpoint.
        registry.add("llm.api-key", () -> "");
        registry.add("llm.base-url", () -> "http://127.0.0.1:9/");
    }

    protected JdbcTemplate superuserJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }
}
