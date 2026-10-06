package dev.rambally.statements.domain.exception;

/** The caller is authenticated but lacks the role the operation requires. */
public final class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(message);
    }
}
