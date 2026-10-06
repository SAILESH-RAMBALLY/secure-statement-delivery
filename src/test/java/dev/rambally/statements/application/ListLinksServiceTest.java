package dev.rambally.statements.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import dev.rambally.statements.application.fakes.FixedTokenGenerator;
import dev.rambally.statements.application.fakes.InMemoryDownloadLinkRepository;
import dev.rambally.statements.application.fakes.InMemoryStatementRepository;
import dev.rambally.statements.application.port.in.LinkSummary;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.DownloadLink;
import dev.rambally.statements.domain.Fixtures;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.LinkPolicy;
import dev.rambally.statements.domain.LinkStatus;
import dev.rambally.statements.domain.Statement;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.exception.StatementNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListLinksServiceTest {

    private static final Principal OWNER = new Principal(new CustomerId("C-1001"), false);
    private static final Principal OTHER = new Principal(new CustomerId("C-2002"), false);

    private final InMemoryStatementRepository statements = new InMemoryStatementRepository();
    private final InMemoryDownloadLinkRepository links = new InMemoryDownloadLinkRepository();
    private final Clock clock = Clock.fixed(Fixtures.NOW.plus(Duration.ofHours(2)), ZoneOffset.UTC);
    private final ListLinksService service = new ListLinksService(statements, links, clock);

    private Statement statement;

    @BeforeEach
    void seed() {
        statement = Fixtures.statement(new CustomerId("C-1001"));
        statements.save(statement);
        LinkPolicy oneHour = new LinkPolicy(Duration.ofHours(1), 1);
        LinkPolicy oneDay = new LinkPolicy(Duration.ofHours(24), 2);
        links.save(DownloadLink.issue(LinkId.newId(), statement, FixedTokenGenerator.tokenNumber(1).hash(), oneHour, Fixtures.NOW));
        links.save(DownloadLink.issue(LinkId.newId(), statement, FixedTokenGenerator.tokenNumber(2).hash(), oneDay, Fixtures.NOW.plusSeconds(10)));
        links.save(DownloadLink.issue(LinkId.newId(), statement, FixedTokenGenerator.tokenNumber(3).hash(), oneDay, Fixtures.NOW.plusSeconds(20))
                .revoke(Fixtures.NOW.plusSeconds(25)));
        links.save(DownloadLink.issue(LinkId.newId(), statement, FixedTokenGenerator.tokenNumber(4).hash(), oneDay, Fixtures.NOW.plusSeconds(30))
                .withDownloadCount(2));
    }

    @Test
    void returns_status_and_counts_newest_first_for_the_owner() {
        List<LinkSummary> result = service.linksFor(statement.id(), OWNER);

        assertThat(result).extracting(LinkSummary::status)
                .containsExactly(LinkStatus.EXHAUSTED, LinkStatus.REVOKED, LinkStatus.ACTIVE, LinkStatus.EXPIRED);
        assertThat(result.get(0).downloadCount()).isEqualTo(2);
        assertThat(result.get(0).maxDownloads()).isEqualTo(2);
    }

    @Test
    void summary_exposes_neither_token_nor_hash() {
        assertThat(Arrays.stream(LinkSummary.class.getRecordComponents()).map(c -> c.getName().toLowerCase()))
                .noneMatch(name -> name.contains("token") || name.contains("hash"));
    }

    @Test
    void non_owner_or_unknown_statement_gets_statement_not_found() {
        assertThatThrownBy(() -> service.linksFor(statement.id(), OTHER)).isInstanceOf(StatementNotFoundException.class);
        assertThatThrownBy(() -> service.linksFor(StatementId.newId(), OWNER)).isInstanceOf(StatementNotFoundException.class);
    }
}
