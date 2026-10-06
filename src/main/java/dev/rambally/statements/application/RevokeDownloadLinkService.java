package dev.rambally.statements.application;

import java.time.Clock;
import java.time.Instant;

import dev.rambally.statements.application.port.in.RevokeDownloadLinkUseCase;
import dev.rambally.statements.application.port.out.AuditLog;
import dev.rambally.statements.application.port.out.DownloadLinkRepository;
import dev.rambally.statements.domain.AuditEvent;
import dev.rambally.statements.domain.DownloadLink;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.exception.LinkNotFoundException;

public final class RevokeDownloadLinkService implements RevokeDownloadLinkUseCase {

    private final DownloadLinkRepository links;
    private final AuditLog audit;
    private final Clock clock;

    public RevokeDownloadLinkService(DownloadLinkRepository links, AuditLog audit, Clock clock) {
        this.links = links;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public void revoke(LinkId linkId, Principal actor) {
        DownloadLink link = links.findById(linkId)
                .filter(l -> actor.mayAccess(l.customerId()))
                .orElseThrow(() -> new LinkNotFoundException(linkId));
        Instant now = clock.instant();
        if (links.revoke(linkId, now)) {
            audit.record(AuditEvent.linkRevoked(now, link.revoke(now)));
        }
    }
}
