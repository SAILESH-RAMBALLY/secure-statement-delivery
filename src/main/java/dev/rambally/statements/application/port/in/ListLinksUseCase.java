package dev.rambally.statements.application.port.in;

import java.util.List;

import dev.rambally.statements.application.Principal;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.exception.StatementNotFoundException;

/** The links issued for one of the caller's statements, newest first. Never includes tokens or hashes. */
public interface ListLinksUseCase {

    /** @throws StatementNotFoundException if the statement is unknown or not accessible to the actor */
    List<LinkSummary> linksFor(StatementId statementId, Principal actor);
}
