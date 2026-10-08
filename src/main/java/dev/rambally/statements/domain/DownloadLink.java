package dev.rambally.statements.domain;

import java.time.Instant;

/**
 * Aggregate root: a time-limited, usage-limited, revocable capability to download one statement.
 * Kept separate from {@link Statement} because it is the thing that mutates under contention.
 * Holds only the hash of the token, never the token.
 */
public record DownloadLink(
        LinkId id,
        StatementId statementId,
        CustomerId customerId,
        TokenHash tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        int maxDownloads,
        int downloadCount,
        Instant revokedAt) {

    public DownloadLink {
        Invariants.notNull(id, "link id");
        Invariants.notNull(statementId, "statement id");
        Invariants.notNull(customerId, "customer id");
        Invariants.notNull(tokenHash, "token hash");
        Invariants.notNull(issuedAt, "issued at");
        Invariants.notNull(expiresAt, "expires at");
        Invariants.require(expiresAt.isAfter(issuedAt), "expiry must be after issue");
        Invariants.require(maxDownloads >= LinkPolicy.MIN_DOWNLOADS && maxDownloads <= LinkPolicy.MAX_DOWNLOADS,
                "max downloads out of range");
        Invariants.require(downloadCount >= 0 && downloadCount <= maxDownloads, "download count out of range");
    }

    public static DownloadLink issue(LinkId id, Statement statement, TokenHash tokenHash, LinkPolicy policy, Instant now) {
        return new DownloadLink(id, statement.id(), statement.customerId(), tokenHash,
                now, now.plus(policy.ttl()), policy.maxDownloads(), 0, null);
    }

    /** Revocation wins over expiry, expiry over exhaustion, so the audit reason is the most decisive one. */
    public Redeemability redeemability(Instant now) {
        if (revokedAt != null) {
            return Redeemability.REVOKED;
        }
        if (!now.isBefore(expiresAt)) {
            return Redeemability.EXPIRED;
        }
        if (downloadCount >= maxDownloads) {
            return Redeemability.EXHAUSTED;
        }
        return Redeemability.OK;
    }

    public LinkStatus status(Instant now) {
        return switch (redeemability(now)) {
            case OK -> LinkStatus.ACTIVE;
            case EXPIRED -> LinkStatus.EXPIRED;
            case REVOKED -> LinkStatus.REVOKED;
            case EXHAUSTED -> LinkStatus.EXHAUSTED;
        };
    }

    /** Idempotent: a second revocation keeps the first timestamp. */
    public DownloadLink revoke(Instant now) {
        if (revokedAt != null) {
            return this;
        }
        return new DownloadLink(id, statementId, customerId, tokenHash, issuedAt, expiresAt, maxDownloads, downloadCount, now);
    }

    public DownloadLink withDownloadCount(int count) {
        return new DownloadLink(id, statementId, customerId, tokenHash, issuedAt, expiresAt, maxDownloads, count, revokedAt);
    }
}
