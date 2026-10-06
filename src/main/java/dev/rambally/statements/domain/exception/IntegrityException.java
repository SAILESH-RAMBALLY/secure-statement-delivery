package dev.rambally.statements.domain.exception;

/** Authenticated decryption failed: the ciphertext, envelope or key is not what was sealed. */
public final class IntegrityException extends DomainException {

    public IntegrityException(String message) {
        super(message);
    }
}
