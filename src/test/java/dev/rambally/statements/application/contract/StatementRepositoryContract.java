package dev.rambally.statements.application.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import dev.rambally.statements.application.port.out.StatementRepository;
import dev.rambally.statements.domain.AccountNumber;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.EncryptionEnvelope;
import dev.rambally.statements.domain.Sha256;
import dev.rambally.statements.domain.Statement;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.StatementPeriod;
import dev.rambally.statements.domain.StorageKey;
import dev.rambally.statements.domain.exception.DuplicateStatementException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behaviour every StatementRepository must have. Run against the in-memory fake, H2 and PostgreSQL so
 * the fast application tests cannot drift from the real adapters.
 *
 * <p>Test methods live in this abstract class, so Spring's test-managed transaction (resolved against the
 * declaring class) does not apply. State is therefore reset explicitly before and after every test.
 */
public abstract class StatementRepositoryContract {

    protected abstract StatementRepository repository();

    /** Remove every row/entry so each test starts from an empty repository. */
    protected abstract void reset();

    @BeforeEach
    void resetBefore() {
        reset();
    }

    @AfterEach
    void resetAfter() {
        reset();
    }

    protected static Statement statement(String customer, String account, String period, Instant createdAt) {
        StatementId id = StatementId.newId();
        byte[] wrapped = new byte[48];
        byte[] dekIv = new byte[12];
        byte[] contentIv = new byte[12];
        for (int i = 0; i < 12; i++) {
            dekIv[i] = (byte) i;
            contentIv[i] = (byte) (100 + i);
        }
        for (int i = 0; i < 48; i++) {
            wrapped[i] = (byte) (200 - i);
        }
        return new Statement(id, new CustomerId(customer), new AccountNumber(account), StatementPeriod.parse(period),
                4321, Sha256.of((customer + period).getBytes(StandardCharsets.UTF_8)), StorageKey.of(id, createdAt),
                new EncryptionEnvelope(1, "kek-v1", wrapped, dekIv, contentIv), createdAt);
    }

    @Test
    void save_find_by_id_round_trips_all_envelope_fields() {
        Statement original = statement("C-1001", "1234567890", "2026-09", Instant.parse("2026-10-01T08:00:00Z"));

        repository().save(original);
        Statement loaded = repository().findById(original.id()).orElseThrow();

        assertThat(loaded).isEqualTo(original);
        assertThat(loaded.envelope()).isEqualTo(original.envelope());
        assertThat(loaded.contentHash()).isEqualTo(original.contentHash());
        assertThat(loaded.createdAt()).isEqualTo(original.createdAt());
    }

    @Test
    void find_by_unknown_id_is_empty() {
        assertThat(repository().findById(StatementId.newId())).isEmpty();
    }

    @Test
    void find_by_customer_orders_by_period_desc_and_excludes_other_customers() {
        Instant t = Instant.parse("2026-10-01T08:00:00Z");
        Statement july = statement("C-1001", "1234567890", "2026-07", t);
        Statement september = statement("C-1001", "1234567890", "2026-09", t.plusSeconds(1));
        Statement august = statement("C-1001", "1234567890", "2026-08", t.plusSeconds(2));
        Statement other = statement("C-2002", "9876543210", "2026-09", t);
        repository().save(july);
        repository().save(september);
        repository().save(august);
        repository().save(other);

        assertThat(repository().findByCustomer(new CustomerId("C-1001")))
                .extracting(s -> s.period().toString())
                .containsExactly("2026-09", "2026-08", "2026-07");
        assertThat(repository().findByCustomer(new CustomerId("C-3003"))).isEmpty();
    }

    @Test
    void duplicate_customer_account_period_throws_duplicate() {
        Instant t = Instant.parse("2026-10-01T08:00:00Z");
        repository().save(statement("C-1001", "1234567890", "2026-09", t));

        assertThatThrownBy(() -> repository().save(statement("C-1001", "1234567890", "2026-09", t.plusSeconds(5))))
                .isInstanceOf(DuplicateStatementException.class);
        assertThat(repository().count()).isEqualTo(1);
    }

    @Test
    void same_period_for_a_different_account_or_customer_is_allowed() {
        Instant t = Instant.parse("2026-10-01T08:00:00Z");
        repository().save(statement("C-1001", "1234567890", "2026-09", t));
        repository().save(statement("C-1001", "1111111111", "2026-09", t));
        repository().save(statement("C-2002", "1234567890", "2026-09", t));

        assertThat(repository().count()).isEqualTo(3);
    }
}
