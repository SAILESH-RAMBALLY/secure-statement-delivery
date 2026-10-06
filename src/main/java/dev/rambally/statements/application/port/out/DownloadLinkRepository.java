package dev.rambally.statements.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.rambally.statements.domain.DownloadLink;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.TokenHash;
import dev.rambally.statements.domain.exception.DuplicateTokenHashException;

public interface DownloadLinkRepository {

    /** @throws DuplicateTokenHashException if another link already has this token hash */
    void save(DownloadLink link);

    Optional<DownloadLink> findByTokenHash(TokenHash hash);

    Optional<DownloadLink> findById(LinkId id);

    /** Newest first. */
    List<DownloadLink> findByStatement(StatementId statementId);

    /**
     * Atomically consume one use: increments the count only if the link is unrevoked, unexpired at
     * {@code now} and below its maximum. True iff exactly one row changed. This is the single arbiter
     * under concurrency; {@code now} comes from the application clock, never the database clock.
     */
    boolean tryConsume(LinkId id, Instant now);

    /** Sets revoked_at only if not already revoked. True iff exactly one row changed. */
    boolean revoke(LinkId id, Instant now);
}
