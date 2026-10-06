package dev.rambally.statements.application.port.out;

import dev.rambally.statements.domain.exception.IntegrityException;

/**
 * Wraps and unwraps per-statement data keys with a key-encryption key the application never sees.
 * Shaped after KMS-style APIs (encrypt/decrypt with an encryption context) so a cloud KMS or Vault
 * Transit adapter is a drop-in replacement for the local adapter.
 */
public interface KeyProvider {

    /** Identifier of the KEK that {@link #wrap} will use right now; stored with each statement. */
    String currentKekId();

    WrappedKey wrap(byte[] dek, byte[] aad);

    /** @throws IntegrityException when the wrapped key, the AAD or the KEK does not match. */
    byte[] unwrap(WrappedKey wrapped, byte[] aad);
}
