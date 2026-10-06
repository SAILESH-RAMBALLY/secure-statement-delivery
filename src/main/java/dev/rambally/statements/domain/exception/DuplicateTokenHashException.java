package dev.rambally.statements.domain.exception;

/** Two links would share a token hash. With 256-bit random tokens this is practically impossible, but the port still names it. */
public final class DuplicateTokenHashException extends DomainException {

    public DuplicateTokenHashException() {
        super("a link with this token hash already exists");
    }
}
