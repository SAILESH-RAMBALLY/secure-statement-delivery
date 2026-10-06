package dev.rambally.statements.adapters.out.persistence;

import dev.rambally.statements.application.port.out.AuditLog;
import dev.rambally.statements.domain.AuditEvent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes each audit row in its own REQUIRES_NEW transaction so it survives a rollback of the caller's
 * work. The whole write, including acquiring a connection and committing, sits inside the catch: a
 * failing audit store is logged (hash prefix only) and counted, never propagated.
 */
@Component
public class JdbcAuditLog implements AuditLog {

    private static final Logger log = LoggerFactory.getLogger(JdbcAuditLog.class);
    public static final String FAILURE_COUNTER = "audit.write.failures";

    private final JdbcClient jdbc;
    private final TransactionTemplate requiresNew;
    private final Counter failures;

    public JdbcAuditLog(JdbcClient jdbc, PlatformTransactionManager transactionManager, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.failures = registry.counter(FAILURE_COUNTER);
    }

    @Override
    public void record(AuditEvent e) {
        try {
            requiresNew.executeWithoutResult(status -> jdbc.sql(
                            "INSERT INTO download_audit (occurred_at, event_type, outcome, token_hash_prefix, link_id, "
                                    + "statement_id, customer_id, actor_id, client_ip, user_agent) VALUES (:occurred_at, :event_type, "
                                    + ":outcome, :token_hash_prefix, :link_id, :statement_id, :customer_id, :actor_id, :client_ip, :user_agent)")
                    .param("occurred_at", JdbcTimes.toDb(e.at()))
                    .param("event_type", e.type().name())
                    .param("outcome", e.outcome() == null ? null : e.outcome().name())
                    .param("token_hash_prefix", e.tokenHashPrefix())
                    .param("link_id", e.linkId() == null ? null : e.linkId().value())
                    .param("statement_id", e.statementId() == null ? null : e.statementId().value())
                    .param("customer_id", e.customerId() == null ? null : e.customerId().value())
                    .param("actor_id", e.actorId() == null ? null : e.actorId().value())
                    .param("client_ip", truncate(e.clientIp(), 45))
                    .param("user_agent", truncate(e.userAgent(), 255))
                    .update());
        } catch (RuntimeException ex) {
            failures.increment();
            log.error("audit write failed for {} event (token prefix {}): {}", e.type(), e.tokenHashPrefix(),
                    ex.getClass().getSimpleName());
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
