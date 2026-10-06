package dev.rambally.statements.domain;

import java.time.Instant;

/**
 * One append-only audit row. Carries the token hash prefix (never the token), the ids involved, who acted
 * (for issue and revoke), and for redemptions the real outcome that the HTTP response deliberately hides.
 */
public record AuditEvent(
        Instant at,
        AuditEventType type,
        RedemptionOutcome outcome,
        String tokenHashPrefix,
        LinkId linkId,
        StatementId statementId,
        CustomerId customerId,
        CustomerId actorId,
        String clientIp,
        String userAgent) {

    public AuditEvent {
        Invariants.notNull(at, "at");
        Invariants.notNull(type, "type");
        Invariants.require(type != AuditEventType.REDEMPTION || outcome != null, "redemption events need an outcome");
        Invariants.require(type == AuditEventType.REDEMPTION || actorId != null, "issue and revoke events need an actor");
    }

    public static AuditEvent linkIssued(Instant at, DownloadLink link, CustomerId actor) {
        return new AuditEvent(at, AuditEventType.LINK_ISSUED, null, link.tokenHash().prefix(),
                link.id(), link.statementId(), link.customerId(), actor, null, null);
    }

    public static AuditEvent linkRevoked(Instant at, DownloadLink link, CustomerId actor) {
        return new AuditEvent(at, AuditEventType.LINK_REVOKED, null, link.tokenHash().prefix(),
                link.id(), link.statementId(), link.customerId(), actor, null, null);
    }

    public static AuditEvent redemption(Instant at, RedemptionOutcome outcome, String tokenHashPrefix,
            DownloadLink link, String clientIp, String userAgent) {
        return new AuditEvent(at, AuditEventType.REDEMPTION, outcome, tokenHashPrefix,
                link == null ? null : link.id(),
                link == null ? null : link.statementId(),
                link == null ? null : link.customerId(),
                null, clientIp, userAgent);
    }
}
