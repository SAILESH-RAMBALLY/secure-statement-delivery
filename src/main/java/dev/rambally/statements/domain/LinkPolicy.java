package dev.rambally.statements.domain;

import java.time.Duration;

/** How long a link lives and how many times it may be redeemed. Bounded so configuration cannot disable the protection. */
public record LinkPolicy(Duration ttl, int maxDownloads) {

    public static final Duration MIN_TTL = Duration.ofMinutes(1);
    public static final Duration MAX_TTL = Duration.ofDays(30);
    public static final int MIN_DOWNLOADS = 1;
    public static final int MAX_DOWNLOADS = 10;

    public LinkPolicy {
        Invariants.notNull(ttl, "ttl");
        Invariants.require(ttl.compareTo(MIN_TTL) >= 0 && ttl.compareTo(MAX_TTL) <= 0, "ttl must be between 1 minute and 30 days");
        Invariants.require(maxDownloads >= MIN_DOWNLOADS && maxDownloads <= MAX_DOWNLOADS, "max downloads must be between 1 and 10");
    }
}
