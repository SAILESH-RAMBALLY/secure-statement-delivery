package dev.rambally.statements.domain;

/** Tiny guard helper so value-object constructors read as a list of invariants. */
final class Invariants {

    private Invariants() {
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    static <T> T notNull(T value, String name) {
        require(value != null, name + " must not be null");
        return value;
    }
}
