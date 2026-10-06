package dev.rambally.statements.application.port.out;

import java.util.Optional;

import dev.rambally.statements.domain.StorageKey;

/**
 * Blob store for statement ciphertext. The byte[] contract is deliberate: ciphertext is bounded by the
 * size policy and maps one-to-one onto an object store's put/get, so an S3 adapter is a straight swap.
 */
public interface StatementStorage {

    void write(StorageKey key, byte[] ciphertext);

    /**
     * Empty when the object is missing <em>or</em> its size differs from {@code expectedLength}: a
     * truncated or swollen file is rejected before any cryptographic work is done.
     */
    Optional<byte[]> read(StorageKey key, long expectedLength);

    /** Idempotent. */
    void delete(StorageKey key);
}
