package dev.rambally.statements.domain.exception;

/**
 * Root of every business failure. Sealed so the web adapter's mapping to HTTP status codes is an
 * exhaustive switch the compiler checks: adding a failure mode without deciding its status is an error.
 */
public abstract sealed class DomainException extends RuntimeException
        permits InvalidStatementException, DuplicateStatementException, StatementNotFoundException,
        LinkNotFoundException, ForbiddenException, LinkNotRedeemableException, IntegrityException,
        DuplicateTokenHashException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
