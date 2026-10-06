package dev.rambally.statements.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@JdbcSliceTest
class FlywayMigrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void v1_creates_the_statement_table_on_h2() {
        List<String> tables = jdbc.sql("SELECT LOWER(table_name) FROM information_schema.tables WHERE table_schema = 'public'")
                .query(String.class).list();

        assertThat(tables).contains("statement");
    }

    @Test
    void migrations_are_recorded_in_order() {
        List<String> versions = jdbc.sql("SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank")
                .query(String.class).list();

        assertThat(versions).startsWith("1");
    }
}
