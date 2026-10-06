package dev.rambally.statements.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class StatementTest {

    @Test
    void is_owned_by_matches_customer_id_only() {
        Statement statement = Fixtures.statement(new CustomerId("C-1001"));

        assertThat(statement.isOwnedBy(new CustomerId("C-1001"))).isTrue();
        assertThat(statement.isOwnedBy(new CustomerId("C-2002"))).isFalse();
    }

    @Test
    void download_file_name_is_statement_account_period_pdf() {
        Statement statement = Fixtures.statement(new CustomerId("C-1001"));

        assertThat(statement.downloadFileName()).isEqualTo("statement-1234567890-2026-09.pdf");
    }

    @Test
    void requires_every_component_and_a_positive_size() {
        Statement s = Fixtures.statement(new CustomerId("C-1001"));
        assertThatThrownBy(() -> new Statement(null, s.customerId(), s.accountNumber(), s.period(), s.sizeBytes(),
                s.contentHash(), s.storageKey(), s.envelope(), s.createdAt()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Statement(s.id(), s.customerId(), s.accountNumber(), s.period(), 0,
                s.contentHash(), s.storageKey(), s.envelope(), s.createdAt()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Statement(s.id(), s.customerId(), s.accountNumber(), s.period(), s.sizeBytes(),
                s.contentHash(), s.storageKey(), s.envelope(), (Instant) null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
