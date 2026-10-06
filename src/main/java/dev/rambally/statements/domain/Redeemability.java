package dev.rambally.statements.domain;

/** Result of the domain pre-check before the atomic consume. */
public enum Redeemability {
    OK, EXPIRED, REVOKED, EXHAUSTED
}
