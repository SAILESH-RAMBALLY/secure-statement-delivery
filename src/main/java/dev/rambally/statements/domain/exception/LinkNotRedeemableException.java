package dev.rambally.statements.domain.exception;

import dev.rambally.statements.domain.RedemptionOutcome;

/**
 * The only failure the download endpoint ever sees. The outcome is for the audit trail; the HTTP
 * response is identical for every value.
 */
public final class LinkNotRedeemableException extends DomainException {

    private final RedemptionOutcome outcome;

    public LinkNotRedeemableException(RedemptionOutcome outcome) {
        super("link not redeemable: " + outcome);
        this.outcome = outcome;
    }

    public RedemptionOutcome outcome() {
        return outcome;
    }
}
