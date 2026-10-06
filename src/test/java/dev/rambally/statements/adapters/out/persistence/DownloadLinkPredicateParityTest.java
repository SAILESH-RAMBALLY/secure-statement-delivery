package dev.rambally.statements.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import dev.rambally.statements.application.contract.StatementRepositoryContract;
import dev.rambally.statements.application.fakes.FixedTokenGenerator;
import dev.rambally.statements.domain.DownloadLink;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.LinkPolicy;
import dev.rambally.statements.domain.Redeemability;
import dev.rambally.statements.domain.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The SQL predicate inside tryConsume and the domain rule DownloadLink.redeemability must agree on every
 * state, including the exact expiry instant; otherwise the audit reason and the actual outcome could differ.
 */
@JdbcSliceTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JdbcStatementRepository.class, JdbcDownloadLinkRepository.class})
class DownloadLinkPredicateParityTest {

    static final Instant ISSUED = Instant.parse("2026-10-06T10:00:00Z");
    static final Duration TTL = Duration.ofHours(1);

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
        statement = StatementRepositoryContract.statement("C-1001", "1234567890", "2026-09", ISSUED);
        statements.save(statement);
    }

    @AfterEach
    void clean() {
        TableCleaner.deleteAll(jdbc);
    }

    static Stream<Arguments> states() {
        Instant expiry = ISSUED.plus(TTL);
        return Stream.of(
                Arguments.of("fresh", 1, 0, null, ISSUED.plusSeconds(1)),
                Arguments.of("one millisecond before expiry", 1, 0, null, expiry.minusMillis(1)),
                Arguments.of("at exact expiry", 1, 0, null, expiry),
                Arguments.of("long expired", 1, 0, null, expiry.plus(Duration.ofDays(3))),
                Arguments.of("revoked", 1, 0, ISSUED.plusSeconds(5), ISSUED.plusSeconds(10)),
                Arguments.of("revoked and expired", 1, 0, ISSUED.plusSeconds(5), expiry.plusSeconds(10)),
                Arguments.of("exhausted single use", 1, 1, null, ISSUED.plusSeconds(1)),
                Arguments.of("count below max", 3, 2, null, ISSUED.plusSeconds(1)),
                Arguments.of("count at max", 3, 3, null, ISSUED.plusSeconds(1)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("states")
    void try_consume_agrees_with_domain_redeemability(String name, int max, int count, Instant revokedAt, Instant now) {
        DownloadLink link = new DownloadLink(LinkId.newId(), statement.id(), statement.customerId(),
                FixedTokenGenerator.tokenNumber(1).hash(), ISSUED, ISSUED.plus(TTL), max, count, revokedAt);
        links.save(link);
        boolean domainSaysOk = link.redeemability(now) == Redeemability.OK;

        boolean consumed = links.tryConsume(link.id(), now);

        assertThat(consumed).as(name).isEqualTo(domainSaysOk);
        assertThat(links.findById(link.id()).orElseThrow().downloadCount()).isEqualTo(domainSaysOk ? count + 1 : count);
    }
}
