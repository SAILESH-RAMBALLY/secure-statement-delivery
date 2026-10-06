package dev.rambally.statements.domain;

public record AccountNumber(String value) {

    public AccountNumber {
        Invariants.require(value != null && value.matches("\\d{6,20}"), "account number must be 6 to 20 digits");
    }

    /** All but the last four digits replaced with '*', for listings and logs. */
    public String masked() {
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    @Override
    public String toString() {
        return masked();
    }
}
