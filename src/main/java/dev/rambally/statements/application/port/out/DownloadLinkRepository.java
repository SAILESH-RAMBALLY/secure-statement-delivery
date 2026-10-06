package dev.rambally.statements.application.port.out;

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
}
