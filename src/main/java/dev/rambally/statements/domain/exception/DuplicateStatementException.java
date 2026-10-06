package dev.rambally.statements.domain.exception;

import dev.rambally.statements.domain.AccountNumber;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.StatementPeriod;

/** A statement for this customer, account and period already exists. */
public final class DuplicateStatementException extends DomainException {

    public DuplicateStatementException(CustomerId customerId, AccountNumber accountNumber, StatementPeriod period) {
        this(customerId, accountNumber, period, null);
    }

    public DuplicateStatementException(CustomerId customerId, AccountNumber accountNumber, StatementPeriod period,
            Throwable cause) {
        super("statement already exists for customer " + customerId + ", account " + accountNumber.masked()
                + ", period " + period, cause);
    }
}
