package dev.rambally.statements.domain;

import static dev.rambally.statements.domain.Fixtures.NOW;
import static dev.rambally.statements.domain.Fixtures.POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class DownloadLinkTest {

    private final Statement statement = Fixtures.statement(new CustomerId("C-1001"));

    @Test
    void issue_sets_expiry_from_policy_ttl_and_zero_count() {
        DownloadLink link = Fixtures.freshLink(statement);

        assertThat(link.statementId()).isEqualTo(statement.id());
        assertThat(link.customerId()).isEqualTo(statement.customerId());
        assertThat(link.issuedAt()).isEqualTo(NOW);
        assertThat(link.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(link.maxDownloads()).isEqualTo(1);
        assertThat(link.downloadCount()).isZero();
        assertThat(link.revokedAt()).isNull();
    }

    @Test
    void fresh_link_is_redeemable() {
        DownloadLink link = Fixtures.freshLink(statement);

        assertThat(link.redeemability(NOW)).isEqualTo(Redeemability.OK);
        assertThat(link.redeemability(NOW.plus(Duration.ofHours(23)))).isEqualTo(Redeemability.OK);
        assertThat(link.status(NOW)).isEqualTo(LinkStatus.ACTIVE);
    }

    @Test
    void link_at_exact_expiry_instant_is_expired() {
        DownloadLink link = Fixtures.freshLink(statement);

        assertThat(link.redeemability(link.expiresAt().minusMillis(1))).isEqualTo(Redeemability.OK);
        assertThat(link.redeemability(link.expiresAt())).isEqualTo(Redeemability.EXPIRED);
        assertThat(link.status(link.expiresAt())).isEqualTo(LinkStatus.EXPIRED);
    }

    @Test
    void revoked_link_reports_revoked_even_if_also_expired() {
        DownloadLink link = Fixtures.freshLink(statement).revoke(NOW.plusSeconds(60));

        assertThat(link.revokedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(link.redeemability(NOW.plus(Duration.ofDays(2)))).isEqualTo(Redeemability.REVOKED);
        assertThat(link.status(NOW.plus(Duration.ofDays(2)))).isEqualTo(LinkStatus.REVOKED);
    }

    @Test
    void link_with_count_equal_to_max_is_exhausted() {
        DownloadLink link = Fixtures.freshLink(statement).withDownloadCount(1);

        assertThat(link.redeemability(NOW)).isEqualTo(Redeemability.EXHAUSTED);
        assertThat(link.status(NOW)).isEqualTo(LinkStatus.EXHAUSTED);
    }

    @Test
    void revoke_is_idempotent_and_keeps_first_revocation_time() {
        DownloadLink once = Fixtures.freshLink(statement).revoke(NOW.plusSeconds(10));
        DownloadLink twice = once.revoke(NOW.plusSeconds(500));

        assertThat(twice.revokedAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void is_owned_by_uses_the_denormalised_customer_id() {
        DownloadLink link = Fixtures.freshLink(statement);

        assertThat(link.isOwnedBy(new CustomerId("C-1001"))).isTrue();
        assertThat(link.isOwnedBy(new CustomerId("C-2002"))).isFalse();
    }

    @Test
    void rejects_inconsistent_state() {
        DownloadLink link = Fixtures.freshLink(statement);
        assertThatThrownBy(() -> link.withDownloadCount(2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> link.withDownloadCount(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadLink(link.id(), link.statementId(), link.customerId(), link.tokenHash(),
                NOW, NOW, 1, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadLink(link.id(), link.statementId(), link.customerId(), link.tokenHash(),
                NOW, NOW.plusSeconds(1), 0, 0, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void policy_bounds_are_enforced() {
        assertThat(POLICY.ttl()).isEqualTo(Duration.ofHours(24));
        assertThatThrownBy(() -> new LinkPolicy(Duration.ofSeconds(59), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinkPolicy(Duration.ofDays(31), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinkPolicy(Duration.ofHours(1), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinkPolicy(Duration.ofHours(1), 11)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new LinkPolicy(Duration.ofMinutes(1), 10).maxDownloads()).isEqualTo(10);
        assertThat(new LinkPolicy(Duration.ofDays(30), 1).ttl()).isEqualTo(Duration.ofDays(30));
    }
}
