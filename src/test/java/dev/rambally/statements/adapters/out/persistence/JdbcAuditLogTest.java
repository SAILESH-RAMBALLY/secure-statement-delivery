package dev.rambally.statements.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import dev.rambally.statements.domain.AuditEvent;
import dev.rambally.statements.domain.AuditEventType;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.RedemptionOutcome;
import dev.rambally.statements.domain.StatementId;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The audit adapter has two guarantees: rows survive a rollback of the surrounding transaction, and it
 * never throws into the caller. Runs outside the test-managed transaction so both can be observed.
 */
@JdbcSliceTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JdbcAuditLogTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PlatformTransactionManager txManager;

    SimpleMeterRegistry registry;
    JdbcAuditLog auditLog;
    ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        TableCleaner.deleteAll(jdbc);
        registry = new SimpleMeterRegistry();
        auditLog = new JdbcAuditLog(jdbc, txManager, registry);
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(JdbcAuditLog.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(JdbcAuditLog.class)).detachAppender(logs);
        TableCleaner.deleteAll(jdbc);
    }

    private static AuditEvent event() {
        return AuditEvent.redemption(Instant.parse("2026-10-06T10:00:00Z"), RedemptionOutcome.EXPIRED, "deadbeef",
                null, "203.0.113.7", "curl/8");
    }

    @Test
    void audit_row_survives_outer_transaction_rollback() {
        TransactionTemplate outer = new TransactionTemplate(txManager);

        outer.executeWithoutResult(status -> {
            auditLog.record(event());
            status.setRollbackOnly();
        });

        long count = jdbc.sql("SELECT COUNT(*) FROM download_audit").query(Long.class).single();
        assertThat(count).isEqualTo(1);
        assertThat(jdbc.sql("SELECT outcome FROM download_audit").query(String.class).single()).isEqualTo("EXPIRED");
    }

    @Test
    void records_every_field_including_nullable_ids() {
        LinkId linkId = LinkId.newId();
        StatementId statementId = StatementId.newId();
        auditLog.record(new AuditEvent(Instant.parse("2026-10-06T10:00:00Z"), AuditEventType.LINK_ISSUED, null, "cafebabe",
                linkId, statementId, new CustomerId("C-1001"), new CustomerId("ops-admin"), null, null));

        var row = jdbc.sql("SELECT event_type, outcome, token_hash_prefix, link_id, statement_id, customer_id, actor_id, client_ip, user_agent "
                + "FROM download_audit").query().singleRow();

        assertThat(row.get("event_type")).isEqualTo("LINK_ISSUED");
        assertThat(row.get("outcome")).isNull();
        assertThat(row.get("token_hash_prefix")).isEqualTo("cafebabe");
        assertThat(row.get("link_id").toString()).isEqualTo(linkId.toString());
        assertThat(row.get("statement_id").toString()).isEqualTo(statementId.toString());
        assertThat(row.get("customer_id")).isEqualTo("C-1001");
        assertThat(row.get("actor_id")).isEqualTo("ops-admin");
        assertThat(row.get("client_ip")).isNull();
    }

    @Test
    void oversize_client_address_and_user_agent_are_truncated_rather_than_dropping_the_row() {
        String longIp = "fe80:0000:0000:0000:0000:0000:0000:0001%enx0123456789ab";
        auditLog.record(AuditEvent.redemption(Instant.parse("2026-10-06T10:00:00Z"), RedemptionOutcome.UNKNOWN_TOKEN,
                "deadbeef", null, longIp, "x".repeat(400)));

        var row = jdbc.sql("SELECT client_ip, user_agent FROM download_audit").query().singleRow();
        assertThat(((String) row.get("client_ip"))).hasSize(45).startsWith("fe80:");
        assertThat(((String) row.get("user_agent"))).hasSize(255);
        assertThat(registry.counter("audit.write.failures").count()).isZero();
    }

    @Test
    void data_access_failure_is_logged_and_counted_not_propagated() {
        jdbc.sql("ALTER TABLE download_audit RENAME TO download_audit_gone").update();
        try {
            assertThatCode(() -> auditLog.record(event())).doesNotThrowAnyException();
        } finally {
            jdbc.sql("ALTER TABLE download_audit_gone RENAME TO download_audit").update();
        }

        assertThat(registry.counter("audit.write.failures").count()).isEqualTo(1.0);
        assertThat(logs.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.ERROR);
            assertThat(e.getFormattedMessage()).contains("deadbeef");
        });
    }

    @Test
    void connection_failure_is_also_swallowed() {
        DriverManagerDataSource dead = new DriverManagerDataSource("jdbc:h2:tcp://127.0.0.1:1/nothing", "sa", "");
        JdbcAuditLog broken = new JdbcAuditLog(JdbcClient.create(dead), new JdbcTransactionManager(dead), registry);

        assertThatCode(() -> broken.record(event())).doesNotThrowAnyException();

        assertThat(registry.counter("audit.write.failures").count()).isEqualTo(1.0);
    }
}
