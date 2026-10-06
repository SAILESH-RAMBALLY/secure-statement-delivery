package dev.rambally.statements.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

import dev.rambally.statements.application.fakes.FixedTokenGenerator;
import dev.rambally.statements.application.fakes.InMemoryDownloadLinkRepository;
import dev.rambally.statements.application.fakes.InMemoryStatementRepository;
import dev.rambally.statements.application.fakes.RecordingAuditLog;
import dev.rambally.statements.application.fakes.RecordingNotificationPort;
import dev.rambally.statements.application.port.in.IssueLinkCommand;
import dev.rambally.statements.application.port.in.IssuedLink;
import dev.rambally.statements.application.port.out.LinkIssuedNotification;
import dev.rambally.statements.domain.AuditEventType;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.DownloadLink;
import dev.rambally.statements.domain.Fixtures;
import dev.rambally.statements.domain.LinkPolicy;
import dev.rambally.statements.domain.LinkToken;
import dev.rambally.statements.domain.Statement;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.exception.StatementNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IssueDownloadLinkServiceTest {

    private static final Principal OWNER = new Principal(new CustomerId("C-1001"), false);
    private static final Principal OTHER = new Principal(new CustomerId("C-2002"), false);
    private static final Principal ADMIN = new Principal(new CustomerId("ops-admin"), true);
    private static final LinkToken TOKEN = FixedTokenGenerator.tokenNumber(7);

    private final InMemoryStatementRepository statements = new InMemoryStatementRepository();
    private final InMemoryDownloadLinkRepository links = new InMemoryDownloadLinkRepository();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final RecordingNotificationPort notifications = new RecordingNotificationPort();
    private final Clock clock = Clock.fixed(Fixtures.NOW, ZoneOffset.UTC);
    private final LinkPolicy policy = new LinkPolicy(Duration.ofHours(2), 3);
    private final PublicBaseUrl baseUrl = new PublicBaseUrl(URI.create("https://statements.example.test"));

    private final IssueDownloadLinkService service = new IssueDownloadLinkService(
            statements, links, new FixedTokenGenerator(TOKEN), audit, notifications, baseUrl, policy, clock);

    private Statement statement;

    @BeforeEach
    void seed() {
        statement = Fixtures.statement(new CustomerId("C-1001"));
        statements.save(statement);
    }

    @Test
    void owner_gets_absolute_url_under_public_base_url_not_host() {
        IssuedLink issued = service.issue(new IssueLinkCommand(statement.id(), OWNER));

        assertThat(issued.url()).isEqualTo(URI.create("https://statements.example.test/download/" + TOKEN.value()));
        assertThat(issued.expiresAt()).isEqualTo(Fixtures.NOW.plus(Duration.ofHours(2)));
        assertThat(issued.maxDownloads()).isEqualTo(3);
    }

    @Test
    void stored_link_holds_hash_not_token_and_policy_values() {
        IssuedLink issued = service.issue(new IssueLinkCommand(statement.id(), OWNER));

        DownloadLink stored = links.findById(issued.linkId()).orElseThrow();
        assertThat(stored.tokenHash()).isEqualTo(TOKEN.hash());
        assertThat(stored.statementId()).isEqualTo(statement.id());
        assertThat(stored.customerId()).isEqualTo(statement.customerId());
        assertThat(stored.issuedAt()).isEqualTo(Fixtures.NOW);
        assertThat(stored.maxDownloads()).isEqualTo(3);
        assertThat(stored.downloadCount()).isZero();
        assertThat(stored.toString()).doesNotContain(TOKEN.value());
    }

    @Test
    void non_owner_gets_statement_not_found_and_nothing_is_recorded() {
        assertThatThrownBy(() -> service.issue(new IssueLinkCommand(statement.id(), OTHER)))
                .isInstanceOf(StatementNotFoundException.class);
        assertThatThrownBy(() -> service.issue(new IssueLinkCommand(StatementId.newId(), OWNER)))
                .isInstanceOf(StatementNotFoundException.class);

        assertThat(links.size()).isZero();
        assertThat(audit.events()).isEmpty();
        assertThat(notifications.sent()).isEmpty();
    }

    @Test
    void an_admin_cannot_issue_a_link_for_a_customer_because_the_response_carries_the_credential() {
        assertThatThrownBy(() -> service.issue(new IssueLinkCommand(statement.id(), ADMIN)))
                .isInstanceOf(StatementNotFoundException.class);

        assertThat(links.size()).isZero();
    }

    @Test
    void audit_and_notification_failures_do_not_undo_an_issued_link() {
        IssueDownloadLinkService fragile = new IssueDownloadLinkService(statements, links, new FixedTokenGenerator(TOKEN),
                new dev.rambally.statements.application.fakes.ThrowingAuditLog(),
                n -> { throw new IllegalStateException("mail server down"); }, baseUrl, policy, clock);

        IssuedLink issued = fragile.issue(new IssueLinkCommand(statement.id(), OWNER));

        assertThat(issued.url().toString()).endsWith("/download/" + TOKEN.value());
        assertThat(links.findById(issued.linkId())).isPresent();
    }

    @Test
    void notification_receives_customer_link_id_url_and_expiry() {
        IssuedLink issued = service.issue(new IssueLinkCommand(statement.id(), OWNER));

        assertThat(notifications.sent()).containsExactly(new LinkIssuedNotification(
                new CustomerId("C-1001"), issued.linkId(), issued.url(), issued.expiresAt(), 3));
    }

    @Test
    void records_LINK_ISSUED_audit_with_hash_prefix_and_the_acting_subject() {
        IssuedLink issued = service.issue(new IssueLinkCommand(statement.id(), OWNER));

        assertThat(audit.ofType(AuditEventType.LINK_ISSUED)).singleElement().satisfies(event -> {
            assertThat(event.at()).isEqualTo(Fixtures.NOW);
            assertThat(event.linkId()).isEqualTo(issued.linkId());
            assertThat(event.statementId()).isEqualTo(statement.id());
            assertThat(event.customerId()).isEqualTo(new CustomerId("C-1001"));
            assertThat(event.actorId()).isEqualTo(OWNER.customerId());
            assertThat(event.tokenHashPrefix()).isEqualTo(TOKEN.hash().prefix());
            assertThat(event.outcome()).isNull();
        });
    }
}
