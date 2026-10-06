package dev.rambally.statements.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;

import dev.rambally.statements.application.crypto.AesGcmEnvelopeCipher;
import dev.rambally.statements.application.fakes.FakeKeyProvider;
import dev.rambally.statements.application.fakes.InMemoryStatementRepository;
import dev.rambally.statements.application.fakes.InMemoryStatementStorage;
import dev.rambally.statements.application.port.in.UploadStatementCommand;
import dev.rambally.statements.domain.AccountNumber;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.Fixtures;
import dev.rambally.statements.domain.Sha256;
import dev.rambally.statements.domain.Statement;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.StatementPeriod;
import dev.rambally.statements.domain.exception.DuplicateStatementException;
import dev.rambally.statements.domain.exception.ForbiddenException;
import dev.rambally.statements.domain.exception.InvalidStatementException;
import dev.rambally.statements.support.TestPdfs;

import org.junit.jupiter.api.Test;

class UploadStatementServiceTest {

    private static final Principal ADMIN = new Principal(new CustomerId("ops-admin"), true);
    private static final Principal CUSTOMER = new Principal(new CustomerId("C-1001"), false);

    private final InMemoryStatementRepository repository = new InMemoryStatementRepository();
    private final InMemoryStatementStorage storage = new InMemoryStatementStorage();
    private final FakeKeyProvider keyProvider = new FakeKeyProvider();
    private final AesGcmEnvelopeCipher cipher = new AesGcmEnvelopeCipher();
    private final Clock clock = Clock.fixed(Fixtures.NOW, ZoneOffset.UTC);

    private final UploadStatementService service = new UploadStatementService(
            repository, storage, keyProvider, cipher, new StatementSizePolicy(10 * 1024 * 1024), clock);

    private UploadStatementCommand command(byte[] pdf, Principal actor) {
        return new UploadStatementCommand(new CustomerId("C-1001"), new AccountNumber("1234567890"),
                StatementPeriod.parse("2026-09"), pdf, actor);
    }

    @Test
    void admin_upload_seals_stores_and_saves_statement_with_plaintext_sha256() {
        byte[] pdf = TestPdfs.minimal();

        StatementId id = service.upload(command(pdf, ADMIN));

        Statement saved = repository.findById(id).orElseThrow();
        assertThat(saved.customerId()).isEqualTo(new CustomerId("C-1001"));
        assertThat(saved.sizeBytes()).isEqualTo(pdf.length);
        assertThat(saved.contentHash()).isEqualTo(Sha256.of(pdf));
        assertThat(saved.createdAt()).isEqualTo(Fixtures.NOW);
        assertThat(saved.envelope().kekId()).isEqualTo("test-kek");

        byte[] stored = storage.read(saved.storageKey(), pdf.length + 16).orElseThrow();
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF-");
        assertThat(cipher.open(stored, id, saved.envelope(), keyProvider)).isEqualTo(pdf);
    }

    @Test
    void non_admin_is_rejected_before_any_side_effect() {
        assertThatThrownBy(() -> service.upload(command(TestPdfs.minimal(), CUSTOMER)))
                .isInstanceOf(ForbiddenException.class);

        assertThat(repository.count()).isZero();
        assertThat(storage.size()).isZero();
    }

    @Test
    void rejects_non_pdf_empty_and_oversize_with_reasons() {
        assertThatThrownBy(() -> service.upload(command("<html>".getBytes(StandardCharsets.US_ASCII), ADMIN)))
                .isInstanceOf(InvalidStatementException.class)
                .extracting(e -> ((InvalidStatementException) e).reason())
                .isEqualTo(InvalidStatementException.Reason.NOT_PDF);
        assertThatThrownBy(() -> service.upload(command(new byte[0], ADMIN)))
                .isInstanceOf(InvalidStatementException.class)
                .extracting(e -> ((InvalidStatementException) e).reason())
                .isEqualTo(InvalidStatementException.Reason.EMPTY);

        UploadStatementService small = new UploadStatementService(repository, storage, keyProvider, cipher,
                new StatementSizePolicy(1024), clock);
        assertThatThrownBy(() -> small.upload(command(TestPdfs.ofSize(2000), ADMIN)))
                .isInstanceOf(InvalidStatementException.class)
                .extracting(e -> ((InvalidStatementException) e).reason())
                .isEqualTo(InvalidStatementException.Reason.TOO_LARGE);

        assertThat(repository.count()).isZero();
        assertThat(storage.size()).isZero();
    }

    @Test
    void rejects_period_after_current_month_using_fixed_clock() {
        UploadStatementCommand future = new UploadStatementCommand(new CustomerId("C-1001"),
                new AccountNumber("1234567890"), StatementPeriod.parse("2026-11"), TestPdfs.minimal(), ADMIN);

        assertThatThrownBy(() -> service.upload(future))
                .isInstanceOf(InvalidStatementException.class)
                .extracting(e -> ((InvalidStatementException) e).reason())
                .isEqualTo(InvalidStatementException.Reason.BAD_PERIOD);

        UploadStatementCommand thisMonth = new UploadStatementCommand(new CustomerId("C-1001"),
                new AccountNumber("1234567890"), StatementPeriod.parse("2026-10"), TestPdfs.minimal(), ADMIN);
        assertThat(service.upload(thisMonth)).isNotNull();
    }

    @Test
    void duplicate_account_period_surfaces_duplicate_statement_exception_and_removes_ciphertext() {
        service.upload(command(TestPdfs.minimal(), ADMIN));

        assertThatThrownBy(() -> service.upload(command(TestPdfs.minimal(), ADMIN)))
                .isInstanceOf(DuplicateStatementException.class);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(storage.size()).isEqualTo(1);
    }

    @Test
    void does_not_save_when_storage_write_fails() {
        storage.failNextWriteWith(new IllegalStateException("disk full"));

        assertThatThrownBy(() -> service.upload(command(TestPdfs.minimal(), ADMIN)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(repository.count()).isZero();
    }

    @Test
    void deletes_stored_ciphertext_when_repository_save_fails() {
        repository.failNextSaveWith(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.upload(command(TestPdfs.minimal(), ADMIN)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(storage.size()).isZero();
    }
}
