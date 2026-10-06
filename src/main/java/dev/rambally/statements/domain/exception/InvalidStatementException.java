package dev.rambally.statements.domain.exception;

/** The uploaded statement is unacceptable; the reason decides whether that is a 400, 413 or 415. */
public final class InvalidStatementException extends DomainException {

    public enum Reason { NOT_PDF, EMPTY, TOO_LARGE, BAD_PERIOD }

    private final Reason reason;

    public InvalidStatementException(Reason reason) {
        super("invalid statement: " + reason);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
