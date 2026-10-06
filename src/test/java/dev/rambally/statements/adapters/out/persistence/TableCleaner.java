package dev.rambally.statements.adapters.out.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Empties every application table in foreign-key order. Used by non-transactional JDBC tests. */
public final class TableCleaner {

    private TableCleaner() {
    }

    public static void deleteAll(JdbcClient jdbc) {
        jdbc.sql("DELETE FROM statement").update();
    }
}
