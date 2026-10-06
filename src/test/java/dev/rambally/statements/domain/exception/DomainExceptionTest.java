package dev.rambally.statements.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rambally.statements.domain.AccountNumber;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.RedemptionOutcome;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.StatementPeriod;

import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    @Test
    void hierarchy_is_sealed_so_the_web_layer_can_switch_exhaustively() {
        assertThat(DomainException.class.isSealed()).isTrue();
        assertThat(DomainException.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(
                        InvalidStatementException.class,
                        DuplicateStatementException.class,
                        StatementNotFoundException.class,
                        LinkNotFoundException.class,
                        ForbiddenException.class,
                        LinkNotRedeemableException.class,
                        IntegrityException.class,
                        DuplicateTokenHashException.class);
    }

    @Test
    void each_subtype_carries_its_reason_and_a_message_without_secrets() {
        var invalid = new InvalidStatementException(InvalidStatementException.Reason.TOO_LARGE);
        assertThat(invalid.reason()).isEqualTo(InvalidStatementException.Reason.TOO_LARGE);
        assertThat(invalid.getMessage()).contains("TOO_LARGE");

        var duplicate = new DuplicateStatementException(new CustomerId("C-1"), new AccountNumber("1234567890"),
                StatementPeriod.parse("2026-09"));
        assertThat(duplicate.getMessage()).contains("2026-09").doesNotContain("1234567890");

        StatementId statementId = StatementId.newId();
        assertThat(new StatementNotFoundException(statementId).statementId()).isEqualTo(statementId);

        LinkId linkId = LinkId.newId();
        assertThat(new LinkNotFoundException(linkId).linkId()).isEqualTo(linkId);

        var notRedeemable = new LinkNotRedeemableException(RedemptionOutcome.EXPIRED);
        assertThat(notRedeemable.outcome()).isEqualTo(RedemptionOutcome.EXPIRED);

        assertThat(new ForbiddenException("admin role required").getMessage()).isEqualTo("admin role required");
        assertThat(new IntegrityException("tag mismatch").getMessage()).isEqualTo("tag mismatch");
        assertThat(new DuplicateTokenHashException().getMessage()).isNotBlank();
    }
}
