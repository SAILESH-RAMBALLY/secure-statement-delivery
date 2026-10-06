package dev.rambally.statements.domain;

import java.util.UUID;

public record LinkId(UUID value) {

    public LinkId {
        Invariants.notNull(value, "link id");
    }

    public static LinkId newId() {
        return new LinkId(UUID.randomUUID());
    }

    public static LinkId of(String value) {
        try {
            return new LinkId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("link id must be a UUID");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
