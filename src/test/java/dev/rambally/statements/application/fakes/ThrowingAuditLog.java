package dev.rambally.statements.application.fakes;

import dev.rambally.statements.application.port.out.AuditLog;
import dev.rambally.statements.domain.AuditEvent;

/**
 * Violates the port contract on purpose (adapters must never throw) so services can prove they do not
 * depend on the adapter honouring it.
 */
public final class ThrowingAuditLog implements AuditLog {

    @Override
    public void record(AuditEvent event) {
        throw new IllegalStateException("audit store unavailable");
    }
}
