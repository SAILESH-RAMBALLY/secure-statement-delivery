package dev.rambally.statements.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import dev.rambally.statements.application.port.in.LinkSummary;
import dev.rambally.statements.application.port.in.ListLinksUseCase;
import dev.rambally.statements.application.port.out.DownloadLinkRepository;
import dev.rambally.statements.application.port.out.StatementRepository;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.exception.StatementNotFoundException;

public final class ListLinksService implements ListLinksUseCase {

    private final StatementRepository statements;
    private final DownloadLinkRepository links;
    private final Clock clock;

    public ListLinksService(StatementRepository statements, DownloadLinkRepository links, Clock clock) {
        this.statements = statements;
        this.links = links;
        this.clock = clock;
    }

    @Override
    public List<LinkSummary> linksFor(StatementId statementId, Principal actor) {
        statements.findById(statementId)
                .filter(s -> actor.mayAccess(s.customerId()))
                .orElseThrow(() -> new StatementNotFoundException(statementId));
        Instant now = clock.instant();
        return links.findByStatement(statementId).stream().map(link -> LinkSummary.of(link, now)).toList();
    }
}
