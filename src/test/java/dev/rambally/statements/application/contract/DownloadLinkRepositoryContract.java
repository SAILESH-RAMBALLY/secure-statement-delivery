package dev.rambally.statements.application.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import dev.rambally.statements.application.fakes.FixedTokenGenerator;
import dev.rambally.statements.application.port.out.DownloadLinkRepository;
import dev.rambally.statements.application.port.out.StatementRepository;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.DownloadLink;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.LinkPolicy;
import dev.rambally.statements.domain.Statement;
import dev.rambally.statements.domain.TokenHash;
import dev.rambally.statements.domain.exception.DuplicateTokenHashException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behaviour every DownloadLinkRepository must have; run against the in-memory fake, H2 and PostgreSQL.
 * Test methods are inherited, so state is reset explicitly rather than relying on test transactions.
 */
public abstract class DownloadLinkRepositoryContract {

    protected static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    protected static final LinkPolicy POLICY = new LinkPolicy(Duration.ofHours(24), 1);

    protected abstract DownloadLinkRepository links();

    protected abstract StatementRepository statements();

    protected abstract void reset();

    protected Statement statement;

    @BeforeEach
    void resetAndSeedStatement() {
        reset();
        statement = StatementRepositoryContract.statement("C-1001", "1234567890", "2026-09", Instant.parse("2026-10-01T08:00:00Z"));
        statements().save(statement);
    }

    @AfterEach
    void resetAfter() {
        reset();
    }

    protected DownloadLink link(TokenHash hash, Instant issuedAt) {
        return DownloadLink.issue(LinkId.newId(), statement, hash, POLICY, issuedAt);
    }

    @Test
    void save_then_find_by_token_hash_round_trips_all_fields() {
        TokenHash hash = FixedTokenGenerator.tokenNumber(1).hash();
        DownloadLink original = link(hash, NOW).withDownloadCount(0);

        links().save(original);

        assertThat(links().findByTokenHash(hash)).contains(original);
        assertThat(links().findById(original.id())).contains(original);
    }

    @Test
    void find_by_unknown_hash_or_id_is_empty() {
        assertThat(links().findByTokenHash(FixedTokenGenerator.tokenNumber(9).hash())).isEmpty();
        assertThat(links().findById(LinkId.newId())).isEmpty();
    }

    @Test
    void duplicate_token_hash_is_rejected() {
        TokenHash hash = FixedTokenGenerator.tokenNumber(2).hash();
        links().save(link(hash, NOW));

        assertThatThrownBy(() -> links().save(link(hash, NOW.plusSeconds(1))))
                .isInstanceOf(DuplicateTokenHashException.class);
    }

    @Test
    void find_by_statement_orders_by_issued_at_desc() {
        links().save(link(FixedTokenGenerator.tokenNumber(3).hash(), NOW));
        links().save(link(FixedTokenGenerator.tokenNumber(4).hash(), NOW.plusSeconds(60)));
        links().save(link(FixedTokenGenerator.tokenNumber(5).hash(), NOW.plusSeconds(30)));

        assertThat(links().findByStatement(statement.id()))
                .extracting(DownloadLink::issuedAt)
                .containsExactly(NOW.plusSeconds(60), NOW.plusSeconds(30), NOW);
        assertThat(links().findByStatement(dev.rambally.statements.domain.StatementId.newId())).isEmpty();
    }

    @Test
    void revoked_state_round_trips() {
        DownloadLink revoked = link(FixedTokenGenerator.tokenNumber(6).hash(), NOW).revoke(NOW.plusSeconds(5));

        links().save(revoked);

        assertThat(links().findById(revoked.id()).orElseThrow().revokedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(links().findById(revoked.id()).orElseThrow().customerId()).isEqualTo(new CustomerId("C-1001"));
    }
}
