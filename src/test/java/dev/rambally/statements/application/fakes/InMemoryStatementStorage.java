package dev.rambally.statements.application.fakes;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.rambally.statements.application.port.out.StatementStorage;
import dev.rambally.statements.domain.StorageKey;

public final class InMemoryStatementStorage implements StatementStorage {

    private final Map<StorageKey, byte[]> files = new ConcurrentHashMap<>();
    private RuntimeException failNextWriteWith;
    private RuntimeException failNextReadWith;
    private RuntimeException failNextDeleteWith;

    public void failNextDeleteWith(RuntimeException e) {
        this.failNextDeleteWith = e;
    }

    public void failNextReadWith(RuntimeException e) {
        this.failNextReadWith = e;
    }

    public void failNextWriteWith(RuntimeException e) {
        this.failNextWriteWith = e;
    }

    @Override
    public void write(StorageKey key, byte[] ciphertext) {
        if (failNextWriteWith != null) {
            RuntimeException e = failNextWriteWith;
            failNextWriteWith = null;
            throw e;
        }
        files.put(key, ciphertext.clone());
    }

    @Override
    public Optional<byte[]> read(StorageKey key, long expectedLength) {
        if (failNextReadWith != null) {
            RuntimeException e = failNextReadWith;
            failNextReadWith = null;
            throw e;
        }
        byte[] bytes = files.get(key);
        if (bytes == null || bytes.length != expectedLength) {
            return Optional.empty();
        }
        return Optional.of(bytes.clone());
    }

    @Override
    public void delete(StorageKey key) {
        if (failNextDeleteWith != null) {
            RuntimeException e = failNextDeleteWith;
            failNextDeleteWith = null;
            throw e;
        }
        files.remove(key);
    }

    public boolean contains(StorageKey key) {
        return files.containsKey(key);
    }

    public int size() {
        return files.size();
    }

    /** Test hook: corrupt a stored ciphertext in place. */
    public void corrupt(StorageKey key) {
        byte[] bytes = files.get(key);
        bytes[bytes.length / 2] ^= 0x01;
    }
}
