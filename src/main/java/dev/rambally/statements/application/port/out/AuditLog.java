package dev.rambally.statements.application.port.out;

import dev.rambally.statements.domain.AuditEvent;

/**
 * Append-only audit trail.
 *
 * <p>Contract for adapters: {@link #record} runs in its own transaction so the row survives a rollback of
 * the caller's work, and it <em>never throws</em>: a failing audit store is reported through logging and
 * metrics, not by turning a customer's download into an error.
 */
public interface AuditLog {

    void record(AuditEvent event);
}
