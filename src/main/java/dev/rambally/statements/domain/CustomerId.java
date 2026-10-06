package dev.rambally.statements.domain;

/**
 * Identity of a customer as asserted by the identity provider (the JWT subject). Deliberately
 * permissive about format: real subjects look like {@code auth0|abc}, UUIDs or e-mail addresses.
 */
public record CustomerId(String value) {

    private static final int MAX_LENGTH = 128;

    public CustomerId {
        Invariants.require(value != null && !value.isBlank(), "customer id must not be blank");
        Invariants.require(value.length() <= MAX_LENGTH, "customer id must be at most 128 characters");
        Invariants.require(value.chars().noneMatch(Character::isISOControl), "customer id must be printable");
    }

    @Override
    public String toString() {
        return value;
    }
}
