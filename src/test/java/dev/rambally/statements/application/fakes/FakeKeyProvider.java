package dev.rambally.statements.application.fakes;

import java.nio.charset.StandardCharsets;

import dev.rambally.statements.adapters.out.crypto.LocalKekKeyProvider;
import dev.rambally.statements.application.port.out.KeyProvider;
import dev.rambally.statements.application.port.out.WrappedKey;

/** Deterministic KEK for application tests; same algorithm as the real local adapter. */
public final class FakeKeyProvider implements KeyProvider {

    private final KeyProvider delegate = new LocalKekKeyProvider(
            "test-kek-test-kek-test-kek-test!".getBytes(StandardCharsets.US_ASCII), "test-kek");

    @Override
    public String currentKekId() {
        return delegate.currentKekId();
    }

    @Override
    public WrappedKey wrap(byte[] dek, byte[] aad) {
        return delegate.wrap(dek, aad);
    }

    @Override
    public byte[] unwrap(WrappedKey wrapped, byte[] aad) {
        return delegate.unwrap(wrapped, aad);
    }
}
