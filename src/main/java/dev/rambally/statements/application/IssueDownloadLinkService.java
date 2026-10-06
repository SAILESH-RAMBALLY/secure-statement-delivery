package dev.rambally.statements.application;

import java.time.Clock;
import java.time.Instant;

import dev.rambally.statements.application.port.in.IssueDownloadLinkUseCase;
import dev.rambally.statements.application.port.in.IssueLinkCommand;
import dev.rambally.statements.application.port.in.IssuedLink;
import dev.rambally.statements.application.port.out.AuditLog;
import dev.rambally.statements.application.port.out.DownloadLinkRepository;
import dev.rambally.statements.application.port.out.LinkIssuedNotification;
import dev.rambally.statements.application.port.out.NotificationPort;
import dev.rambally.statements.application.port.out.StatementRepository;
import dev.rambally.statements.application.port.out.TokenGenerator;
import dev.rambally.statements.domain.AuditEvent;
import dev.rambally.statements.domain.DownloadLink;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.LinkPolicy;
import dev.rambally.statements.domain.LinkToken;
import dev.rambally.statements.domain.Statement;
import dev.rambally.statements.domain.exception.StatementNotFoundException;

public final class IssueDownloadLinkService implements IssueDownloadLinkUseCase {

    private final StatementRepository statements;
    private final DownloadLinkRepository links;
    private final TokenGenerator tokens;
    private final AuditLog audit;
    private final NotificationPort notifications;
    private final PublicBaseUrl baseUrl;
    private final LinkPolicy policy;
    private final Clock clock;

    public IssueDownloadLinkService(StatementRepository statements, DownloadLinkRepository links, TokenGenerator tokens,
            AuditLog audit, NotificationPort notifications, PublicBaseUrl baseUrl, LinkPolicy policy, Clock clock) {
        this.statements = statements;
        this.links = links;
        this.tokens = tokens;
        this.audit = audit;
        this.notifications = notifications;
        this.baseUrl = baseUrl;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public IssuedLink issue(IssueLinkCommand command) {
        Statement statement = statements.findById(command.statementId())
                .filter(s -> command.actor().mayAccess(s.customerId()))
                .orElseThrow(() -> new StatementNotFoundException(command.statementId()));

        Instant now = clock.instant();
        LinkToken token = tokens.next();
        DownloadLink link = DownloadLink.issue(LinkId.newId(), statement, token.hash(), policy, now);
        links.save(link);
        audit.record(AuditEvent.linkIssued(now, link));

        IssuedLink issued = new IssuedLink(link.id(), baseUrl.downloadUrl(token), link.expiresAt(), link.maxDownloads());
        notifications.linkIssued(new LinkIssuedNotification(statement.customerId(), link.id(), issued.url(),
                issued.expiresAt(), issued.maxDownloads()));
        return issued;
    }
}
