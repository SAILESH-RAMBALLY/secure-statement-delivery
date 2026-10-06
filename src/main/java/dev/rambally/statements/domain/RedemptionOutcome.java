package dev.rambally.statements.domain;

/**
 * Why a redemption attempt succeeded or failed. Recorded in the audit trail; never revealed to the
 * caller, who sees one uniform 404 for everything except SUCCESS.
 */
public enum RedemptionOutcome {
    SUCCESS,
    /** No link has this token's hash. */
    UNKNOWN_TOKEN,
    /** The path segment was not even token-shaped. */
    MALFORMED_TOKEN,
    EXPIRED,
    REVOKED,
    /** The count was already at the maximum when the link was read. */
    EXHAUSTED,
    /** The pre-check passed but the conditional UPDATE changed no row: a concurrent redeemer won. */
    LOST_RACE,
    /** GCM tag verification failed: tampered or corrupted ciphertext, or wrong key. */
    INTEGRITY_FAILED,
    /** The ciphertext file is missing or has the wrong size. */
    STORAGE_MISSING
}
