package dev.rambally.statements.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Where a statement's ciphertext lives inside the storage root: {@code yyyy/MM/<statementId>.bin}.
 * Derived only from server-side values, so no client input can ever influence a path.
 */
public record StorageKey(String value) {

    private static final DateTimeFormatter YEAR_MONTH_DIRS = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);

    public StorageKey {
        Invariants.require(value != null && !value.isBlank(), "storage key must not be blank");
        Invariants.require(!value.startsWith("/"), "storage key must be relative");
        Invariants.require(!value.contains("\\"), "storage key must not contain backslashes");
        Invariants.require(!value.contains(".."), "storage key must not contain parent references");
    }

    public static StorageKey of(StatementId id, Instant createdAt) {
        return new StorageKey(YEAR_MONTH_DIRS.format(createdAt) + "/" + id + ".bin");
    }

    @Override
    public String toString() {
        return value;
    }
}
