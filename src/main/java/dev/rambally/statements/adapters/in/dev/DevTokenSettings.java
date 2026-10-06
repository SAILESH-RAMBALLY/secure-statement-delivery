package dev.rambally.statements.adapters.in.dev;

/** Claims the development issuer stamps on every token; supplied by bootstrap so the adapter does not depend on it. */
public record DevTokenSettings(String issuer, String audience, String rolesClaim) {
}
