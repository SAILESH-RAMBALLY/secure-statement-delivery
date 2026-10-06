package dev.rambally.statements.domain.exception;

import dev.rambally.statements.domain.StatementId;

/** Unknown statement, or one the caller does not own: the two are deliberately indistinguishable. */
public final class StatementNotFoundException extends DomainException {

    private final StatementId statementId;

    public StatementNotFoundException(StatementId statementId) {
        super("statement not found: " + statementId);
        this.statementId = statementId;
    }

    public StatementId statementId() {
        return statementId;
    }
}
