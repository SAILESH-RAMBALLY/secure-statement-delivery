package dev.rambally.statements.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import dev.rambally.statements.application.fakes.InMemoryStatementRepository;
import dev.rambally.statements.application.port.in.StatementSummary;
import dev.rambally.statements.domain.AccountNumber;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.EncryptionEnvelope;
import dev.rambally.statements.domain.Sha256;
import dev.rambally.statements.domain.Statement;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.StatementPeriod;
import dev.rambally.statements.domain.StorageKey;

import org.junit.jupiter.api.Test;

class ListStatementsServiceTest {

    private final InMemoryStatementRepository statements = new InMemoryStatementRepository();
    private final ListStatementsService service = new ListStatementsService(statements);

    private static Statement statement(String customer, String period) {
        StatementId id = StatementId.newId();
        Instant created = Instant.parse("2026-10-01T08:00:00Z");
        return new Statement(id, new CustomerId(customer), new AccountNumber("1234567890"), StatementPeriod.parse(period),
                2048, Sha256.of(period.getBytes(StandardCharsets.UTF_8)), StorageKey.of(id, created),
                new EncryptionEnvelope(1, "k", new byte[48], new byte[12], new byte[12]), created);
    }

    @Test
    void returns_only_the_callers_statements_newest_first_with_masked_account() {
        statements.save(statement("C-1001", "2026-07"));
        statements.save(statement("C-1001", "2026-09"));
        statements.save(statement("C-2002", "2026-09"));

        var result = service.listFor(new Principal(new CustomerId("C-1001"), false));

        assertThat(result).extracting(StatementSummary::period).extracting(Object::toString)
                .containsExactly("2026-09", "2026-07");
        assertThat(result).allSatisfy(s -> {
            assertThat(s.maskedAccountNumber()).isEqualTo("******7890");
            assertThat(s.sizeBytes()).isEqualTo(2048);
        });
    }

    @Test
    void an_admin_listing_sees_only_their_own_subject_too() {
        statements.save(statement("C-1001", "2026-07"));

        assertThat(service.listFor(new Principal(new CustomerId("ops-admin"), true))).isEmpty();
    }
}
