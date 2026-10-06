package dev.rambally.statements.domain;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/** Shared domain fixtures. Time is always fixed so tests are deterministic. */
public final class Fixtures {

    public static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    public static final LinkPolicy POLICY = new LinkPolicy(Duration.ofHours(24), 1);

    private Fixtures() {
    }

    public static Statement statement(CustomerId customerId) {
        StatementId id = StatementId.of("550e8400-e29b-41d4-a716-446655440000");
        Instant createdAt = Instant.parse("2026-10-01T08:00:00Z");
        return new Statement(
                id,
                customerId,
                new AccountNumber("1234567890"),
                StatementPeriod.parse("2026-09"),
                1024,
                Sha256.of("%PDF-1.4 fixture".getBytes(StandardCharsets.US_ASCII)),
                StorageKey.of(id, createdAt),
                new EncryptionEnvelope(1, "kek-v1", new byte[48], new byte[12], new byte[12]),
                createdAt);
    }

    public static LinkToken token() {
        return LinkToken.parse("A".repeat(43)).orElseThrow();
    }

    public static DownloadLink freshLink(Statement statement) {
        return DownloadLink.issue(LinkId.newId(), statement, token().hash(), POLICY, NOW);
    }
}
