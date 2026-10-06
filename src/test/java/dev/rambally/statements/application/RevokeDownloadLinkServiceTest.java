package dev.rambally.statements.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.ZoneOffset;

import dev.rambally.statements.application.fakes.FixedTokenGenerator;
import dev.rambally.statements.application.fakes.InMemoryDownloadLinkRepository;
import dev.rambally.statements.application.fakes.RecordingAuditLog;
import dev.rambally.statements.domain.AuditEventType;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.DownloadLink;
import dev.rambally.statements.domain.Fixtures;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.Statement;
import dev.rambally.statements.domain.exception.LinkNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RevokeDownloadLinkServiceTest {

    private static final Principal OWNER = new Principal(new CustomerId("C-1001"), false);
    private static final Principal OTHER = new Principal(new CustomerId("C-2002"), false);
    private static final Principal ADMIN = new Principal(new CustomerId("ops-admin"), true);

    private final InMemoryDownloadLinkRepository links = new InMemoryDownloadLinkRepository();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final Clock clock = Clock.fixed(Fixtures.NOW.plusSeconds(30), ZoneOffset.UTC);
    private final RevokeDownloadLinkService service = new RevokeDownloadLinkService(links, audit, clock);

    private DownloadLink link;

    @BeforeEach
    void seed() {
        Statement statement = Fixtures.statement(new CustomerId("C-1001"));
        link = DownloadLink.issue(LinkId.newId(), statement, FixedTokenGenerator.tokenNumber(3).hash(), Fixtures.POLICY, Fixtures.NOW);
        links.save(link);
    }

    @Test
    void owner_revokes_and_LINK_REVOKED_is_recorded() {
        service.revoke(link.id(), OWNER);

        DownloadLink revoked = links.findById(link.id()).orElseThrow();
        assertThat(revoked.revokedAt()).isEqualTo(Fixtures.NOW.plusSeconds(30));
        assertThat(revoked.redeemability(Fixtures.NOW.plusSeconds(31))).isEqualTo(dev.rambally.statements.domain.Redeemability.REVOKED);
        assertThat(audit.ofType(AuditEventType.LINK_REVOKED)).singleElement().satisfies(e -> {
            assertThat(e.linkId()).isEqualTo(link.id());
            assertThat(e.customerId()).isEqualTo(new CustomerId("C-1001"));
            assertThat(e.tokenHashPrefix()).isEqualTo(link.tokenHash().prefix());
        });
    }

    @Test
    void repeat_revocation_is_idempotent_and_not_audited_twice() {
        service.revoke(link.id(), OWNER);

        assertThatCode(() -> service.revoke(link.id(), OWNER)).doesNotThrowAnyException();

        assertThat(links.findById(link.id()).orElseThrow().revokedAt()).isEqualTo(Fixtures.NOW.plusSeconds(30));
        assertThat(audit.ofType(AuditEventType.LINK_REVOKED)).hasSize(1);
    }

    @Test
    void admin_can_revoke_any_link() {
        service.revoke(link.id(), ADMIN);

        assertThat(links.findById(link.id()).orElseThrow().revokedAt()).isNotNull();
    }

    @Test
    void non_owner_and_unknown_link_get_link_not_found_and_nothing_changes() {
        assertThatThrownBy(() -> service.revoke(link.id(), OTHER)).isInstanceOf(LinkNotFoundException.class);
        assertThatThrownBy(() -> service.revoke(LinkId.newId(), OWNER)).isInstanceOf(LinkNotFoundException.class);

        assertThat(links.findById(link.id()).orElseThrow().revokedAt()).isNull();
        assertThat(audit.events()).isEmpty();
    }
}
