package dev.rambally.statements.domain;

import java.time.Instant;

/**
 * Aggregate root: an encrypted statement PDF and the metadata needed to find, authorise and decrypt it.
 * Immutable; the only thing that ever changes about a statement is which links point at it.
 */
public record Statement(
        StatementId id,
        CustomerId customerId,
        AccountNumber accountNumber,
        StatementPeriod period,
        long sizeBytes,
        Sha256 contentHash,
        StorageKey storageKey,
        EncryptionEnvelope envelope,
        Instant createdAt) {

    public Statement {
        Invariants.notNull(id, "statement id");
        Invariants.notNull(customerId, "customer id");
        Invariants.notNull(accountNumber, "account number");
        Invariants.notNull(period, "period");
        Invariants.require(sizeBytes > 0, "size must be positive");
        Invariants.notNull(contentHash, "content hash");
        Invariants.notNull(storageKey, "storage key");
        Invariants.notNull(envelope, "encryption envelope");
        Invariants.notNull(createdAt, "created at");
    }

    public boolean isOwnedBy(CustomerId candidate) {
        return customerId.equals(candidate);
    }

    /** Server-derived, ASCII-only file name; the uploaded file name is never stored or echoed. */
    public String downloadFileName() {
        return "statement-" + accountNumber.value() + "-" + period + ".pdf";
    }
}
