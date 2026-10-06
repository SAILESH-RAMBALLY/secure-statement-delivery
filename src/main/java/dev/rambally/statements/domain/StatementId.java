package dev.rambally.statements.domain;

import java.util.UUID;

public record StatementId(UUID value) {

    public StatementId {
        Invariants.notNull(value, "statement id");
    }

    public static StatementId newId() {
        return new StatementId(UUID.randomUUID());
    }

    public static StatementId of(String value) {
        try {
            return new StatementId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("statement id must be a UUID");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
