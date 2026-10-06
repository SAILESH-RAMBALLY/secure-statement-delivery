package dev.rambally.statements.adapters.out.persistence;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Empties every application table in foreign-key order. Used by non-transactional JDBC tests.
 *
 * <p>On PostgreSQL the audit table is append-only (trigger from V4), so the reset briefly disables its
 * triggers; that is a test-only privilege the application role would not have in production.
 */
public final class TableCleaner {

    private TableCleaner() {
    }

    public static void deleteAll(JdbcClient jdbc) {
        resetAudit(jdbc);
        jdbc.sql("DELETE FROM download_link").update();
        jdbc.sql("DELETE FROM statement").update();
    }

    public static void resetAudit(JdbcClient jdbc) {
        try {
            jdbc.sql("DELETE FROM download_audit").update();
        } catch (DataAccessException appendOnly) {
            jdbc.sql("ALTER TABLE download_audit DISABLE TRIGGER ALL").update();
            jdbc.sql("DELETE FROM download_audit").update();
            jdbc.sql("ALTER TABLE download_audit ENABLE TRIGGER ALL").update();
        }
    }
}
