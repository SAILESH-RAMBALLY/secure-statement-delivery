package dev.rambally.statements.application.port.in;

import dev.rambally.statements.domain.exception.StatementNotFoundException;

/** Issue a time-limited, usage-limited download link for one of the caller's statements. */
public interface IssueDownloadLinkUseCase {

    /** @throws StatementNotFoundException if the statement is unknown or not owned by the actor (indistinguishable) */
    IssuedLink issue(IssueLinkCommand command);
}
