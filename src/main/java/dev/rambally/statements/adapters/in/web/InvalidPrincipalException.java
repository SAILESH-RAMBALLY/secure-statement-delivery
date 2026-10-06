package dev.rambally.statements.adapters.in.web;

/** The request is authenticated but its token cannot be turned into an application principal. Mapped to 401. */
public final class InvalidPrincipalException extends RuntimeException {

    public InvalidPrincipalException(String message) {
        super(message);
    }
}
