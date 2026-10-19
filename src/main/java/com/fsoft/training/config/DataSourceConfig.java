package com.fsoft.training.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Two DataSources against the same database: "app" (read/write, used by Flyway and app data)
 * and "analyst" (read-only role, the only one allowed to run LLM-generated SQL).
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource.app")
    public DataSourceProperties appDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource appDataSource(@Qualifier("appDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
    }

    @Bean
    @Primary
    public JdbcTemplate appJdbcTemplate(DataSource appDataSource) {
        return new JdbcTemplate(appDataSource);
    }

    @Bean
    @ConfigurationProperties("app.datasource.analyst")
    public DataSourceProperties analystDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Qualifier("analyst")
    public DataSource analystDataSource(@Qualifier("analystDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
    }

    @Bean
    @Qualifier("analyst")
    public JdbcTemplate analystJdbcTemplate(@Qualifier("analyst") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
