package dev.rambally.statements.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.Instant;

import dev.rambally.statements.domain.AuditEvent;
import dev.rambally.statements.domain.RedemptionOutcome;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** On PostgreSQL the audit table is append-only at the database level, not just by convention. */
@PostgresSliceIT
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DownloadAuditAppendOnlyIT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PlatformTransactionManager txManager;

    @BeforeEach
    void seedOneRow() {
        TableCleaner.resetAudit(jdbc);
        new JdbcAuditLog(jdbc, txManager, new SimpleMeterRegistry()).record(AuditEvent.redemption(
                Instant.parse("2026-10-06T10:00:00Z"), RedemptionOutcome.SUCCESS, "cafebabe", null, "203.0.113.7", "curl"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM download_audit").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void update_delete_and_truncate_are_rejected_by_the_trigger() {
        assertRejected(() -> jdbc.sql("UPDATE download_audit SET outcome = 'EXPIRED'").update());
        assertRejected(() -> jdbc.sql("DELETE FROM download_audit").update());
        assertRejected(() -> jdbc.sql("TRUNCATE download_audit").update());

        assertThat(jdbc.sql("SELECT outcome FROM download_audit").query(String.class).single()).isEqualTo("SUCCESS");
    }

    private static void assertRejected(Runnable mutation) {
        assertThatThrownBy(mutation::run)
                .isInstanceOf(DataAccessException.class)
                .satisfies(e -> {
                    Throwable cause = e.getCause();
                    assertThat(cause).isInstanceOf(SQLException.class);
                    assertThat(((SQLException) cause).getSQLState()).isEqualTo("42501");
                    assertThat(cause.getMessage()).contains("append-only");
                });
    }
}
