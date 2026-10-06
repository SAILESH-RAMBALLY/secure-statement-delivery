package dev.rambally.statements.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import dev.rambally.statements.application.contract.StatementRepositoryContract;
import dev.rambally.statements.application.fakes.FixedTokenGenerator;
import dev.rambally.statements.domain.DownloadLink;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.LinkPolicy;
import dev.rambally.statements.domain.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The same race on real PostgreSQL, repeated so a race that slips through once is still caught. */
@PostgresSliceIT
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JdbcStatementRepository.class, JdbcDownloadLinkRepository.class})
class DownloadLinkConcurrencyIT {

    static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    static final int THREADS = 64;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JdbcStatementRepository statements;

    @Autowired
    JdbcDownloadLinkRepository links;

    Statement statement;

    @BeforeEach
    void seed() {
        TableCleaner.deleteAll(jdbc);
        statement = StatementRepositoryContract.statement("C-1001", "1234567890", "2026-09", NOW);
        statements.save(statement);
    }

    @AfterEach
    void clean() {
        TableCleaner.deleteAll(jdbc);
    }

    @RepeatedTest(5)
    void sixty_four_threads_single_use_link_exactly_one_succeeds_on_postgres() throws Exception {
        DownloadLink link = DownloadLink.issue(LinkId.newId(), statement, FixedTokenGenerator.tokenNumber(1).hash(),
                new LinkPolicy(Duration.ofHours(1), 1), NOW);
        links.save(link);

        int successes = ConcurrentConsume.race(links, link.id(), NOW.plusSeconds(1), THREADS);

        assertThat(successes).isEqualTo(1);
        assertThat(links.findById(link.id()).orElseThrow().downloadCount()).isEqualTo(1);
    }

    @RepeatedTest(3)
    void max_three_link_under_contention_is_consumed_exactly_three_times_on_postgres() throws Exception {
        DownloadLink link = DownloadLink.issue(LinkId.newId(), statement, FixedTokenGenerator.tokenNumber(2).hash(),
                new LinkPolicy(Duration.ofHours(1), 3), NOW);
        links.save(link);

        int successes = ConcurrentConsume.race(links, link.id(), NOW.plusSeconds(1), THREADS);

        assertThat(successes).isEqualTo(3);
        assertThat(links.findById(link.id()).orElseThrow().downloadCount()).isEqualTo(3);
    }
}
