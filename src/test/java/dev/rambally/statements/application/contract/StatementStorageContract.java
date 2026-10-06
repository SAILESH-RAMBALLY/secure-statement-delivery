package dev.rambally.statements.application.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import dev.rambally.statements.application.port.out.StatementStorage;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.StorageKey;

import org.junit.jupiter.api.Test;

/** Behaviour every StatementStorage must have; run against the in-memory fake and the filesystem adapter. */
public abstract class StatementStorageContract {

    protected abstract StatementStorage storage();

    protected static StorageKey key() {
        return StorageKey.of(StatementId.newId(), Instant.parse("2026-10-01T08:00:00Z"));
    }

    @Test
    void write_then_read_round_trips() {
        StorageKey key = key();
        byte[] bytes = {1, 2, 3, 4, 5, 6, 7, 8, 9};

        storage().write(key, bytes);

        assertThat(storage().read(key, bytes.length)).contains(bytes);
    }

    @Test
    void read_missing_is_empty() {
        assertThat(storage().read(key(), 10)).isEmpty();
    }

    @Test
    void read_with_wrong_expected_length_is_empty() {
        StorageKey key = key();
        storage().write(key, new byte[20]);

        assertThat(storage().read(key, 19)).isEmpty();
        assertThat(storage().read(key, 21)).isEmpty();
        assertThat(storage().read(key, 20)).isPresent();
    }

    @Test
    void delete_is_idempotent() {
        StorageKey key = key();
        storage().write(key, new byte[3]);

        storage().delete(key);
        storage().delete(key);

        assertThat(storage().read(key, 3)).isEmpty();
    }

    @Test
    void returned_bytes_are_a_copy() {
        StorageKey key = key();
        byte[] bytes = {9, 9, 9};
        storage().write(key, bytes);
        bytes[0] = 0;

        byte[] read = storage().read(key, 3).orElseThrow();
        read[1] = 0;

        assertThat(storage().read(key, 3)).contains(new byte[] {9, 9, 9});
    }
}
