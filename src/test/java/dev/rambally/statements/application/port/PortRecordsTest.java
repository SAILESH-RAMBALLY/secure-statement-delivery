package dev.rambally.statements.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rambally.statements.application.Principal;
import dev.rambally.statements.application.port.in.IssueLinkCommand;
import dev.rambally.statements.application.port.in.StatementDownload;
import dev.rambally.statements.application.port.in.UploadStatementCommand;
import dev.rambally.statements.application.port.out.WrappedKey;
import dev.rambally.statements.domain.AccountNumber;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.StatementPeriod;

import org.junit.jupiter.api.Test;

/** The port records guard their own completeness; byte-array payloads are handed over, not copied. */
class PortRecordsTest {

    private static final Principal ACTOR = new Principal(new CustomerId("C-1"), true);

    @Test
    void upload_command_requires_every_part_and_takes_ownership_of_the_bytes_without_copying() {
        byte[] bytes = {1, 2, 3};
        UploadStatementCommand command = new UploadStatementCommand(new CustomerId("C-1"), new AccountNumber("1234567890"),
                StatementPeriod.parse("2026-09"), bytes, ACTOR);

        assertThat(command.pdfBytes()).isSameAs(bytes); // transfer object: one statement-sized allocation, not two
        assertThat(new UploadStatementCommand(new CustomerId("C-1"), new AccountNumber("1234567890"),
                StatementPeriod.parse("2026-09"), null, ACTOR).pdfBytes()).isEmpty();
        assertThatThrownBy(() -> new UploadStatementCommand(null, new AccountNumber("1234567890"),
                StatementPeriod.parse("2026-09"), bytes, ACTOR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UploadStatementCommand(new CustomerId("C-1"), new AccountNumber("1234567890"),
                StatementPeriod.parse("2026-09"), bytes, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void issue_command_requires_statement_and_actor() {
        assertThatThrownBy(() -> new IssueLinkCommand(null, ACTOR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IssueLinkCommand(StatementId.newId(), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statement_download_is_a_transfer_object_that_does_not_copy_and_rejects_nulls() {
        byte[] bytes = {5, 5};
        StatementDownload download = new StatementDownload("a.pdf", bytes);

        assertThat(download.pdf()).isSameAs(bytes);
        assertThatThrownBy(() -> new StatementDownload(null, bytes)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StatementDownload("a.pdf", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wrapped_key_validates_and_copies_and_redacts() {
        byte[] wrapped = new byte[48];
        WrappedKey key = new WrappedKey("kek", wrapped, new byte[12]);
        wrapped[0] = 1;

        assertThat(key.wrappedDek()[0]).isZero();
        assertThat(key.toString()).isEqualTo("WrappedKey[kekId=kek]");
        assertThatThrownBy(() -> new WrappedKey(" ", wrapped, new byte[12])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WrappedKey("kek", null, new byte[12])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WrappedKey("kek", wrapped, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void principal_requires_a_customer_id_and_admins_may_access_everything() {
        assertThatThrownBy(() -> new Principal(null, false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ACTOR.mayAccess(new CustomerId("someone-else"))).isTrue();
        assertThat(new Principal(new CustomerId("C-1"), false).mayAccess(new CustomerId("C-2"))).isFalse();
        assertThat(new Principal(new CustomerId("C-1"), false).mayAccess(new CustomerId("C-1"))).isTrue();
    }
}
