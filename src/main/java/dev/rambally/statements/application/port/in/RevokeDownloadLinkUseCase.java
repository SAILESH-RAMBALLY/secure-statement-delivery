package dev.rambally.statements.application.port.in;

import dev.rambally.statements.application.Principal;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.exception.LinkNotFoundException;

/** Revoke a link immediately. Idempotent: revoking a revoked link is a no-op. */
public interface RevokeDownloadLinkUseCase {

    /** @throws LinkNotFoundException if the link is unknown or not accessible to the actor */
    void revoke(LinkId linkId, Principal actor);
}
