package dev.rambally.statements.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** Null guards, equality against other types and the far ends of every bound. */
class DomainEdgeCasesTest {

    @Test
    void encryption_envelope_rejects_nulls_and_compares_every_component() {
        byte[] w = new byte[48];
        byte[] iv = new byte[12];
        EncryptionEnvelope env = new EncryptionEnvelope(1, "k", w, iv, iv);

        assertThatThrownBy(() -> new EncryptionEnvelope(1, null, w, iv, iv)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncryptionEnvelope(1, "k", null, iv, iv)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncryptionEnvelope(1, "k", w, null, iv)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncryptionEnvelope(1, "k", w, iv, null)).isInstanceOf(IllegalArgumentException.class);

        byte[] otherW = new byte[48];
        otherW[0] = 1;
        byte[] otherIv = new byte[12];
        otherIv[0] = 1;
        assertThat(env).isNotEqualTo(null).isNotEqualTo("string")
                .isNotEqualTo(new EncryptionEnvelope(1, "other", w, iv, iv))
                .isNotEqualTo(new EncryptionEnvelope(1, "k", otherW, iv, iv))
                .isNotEqualTo(new EncryptionEnvelope(1, "k", w, otherIv, iv))
                .isNotEqualTo(new EncryptionEnvelope(1, "k", w, iv, otherIv));
        assertThat(env.toString()).contains("kekId=k").doesNotContain("[B@");
    }

    @Test
    void download_link_rejects_max_downloads_above_the_ceiling() {
        Statement statement = Fixtures.statement(new CustomerId("C-1001"));
        TokenHash hash = Fixtures.token().hash();

        assertThatThrownBy(() -> new DownloadLink(LinkId.newId(), statement.id(), statement.customerId(), hash,
                Fixtures.NOW, Fixtures.NOW.plusSeconds(60), 11, 0, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void token_hash_and_sha256_reject_null_and_compare_only_with_their_own_type() {
        TokenHash hash = Fixtures.token().hash();
        Sha256 sha = Sha256.of(new byte[] {1});

        assertThatThrownBy(() -> new TokenHash(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Sha256(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(hash).isNotEqualTo(null).isNotEqualTo(sha).isNotEqualTo(new TokenHash(new byte[32]));
        assertThat(sha).isNotEqualTo(null).isNotEqualTo(hash).isNotEqualTo(Sha256.of(new byte[] {2}));
        assertThat(hash.toString()).startsWith("TokenHash[").endsWith("...]");
    }

    @Test
    void storage_key_and_link_token_reject_null_in_their_constructors() {
        assertThatThrownBy(() -> new StorageKey(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinkToken(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void audit_event_requires_an_outcome_for_redemptions_only() {
        assertThatThrownBy(() -> new AuditEvent(Instant.EPOCH, AuditEventType.REDEMPTION, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new AuditEvent(Instant.EPOCH, AuditEventType.LINK_ISSUED, null, null, null, null, null, null, null).outcome()).isNull();
        assertThatThrownBy(() -> new AuditEvent(null, AuditEventType.LINK_ISSUED, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pdf_document_rejects_inputs_shorter_than_the_magic_header_and_compares_only_with_its_type() {
        assertThatThrownBy(() -> new PdfDocument("%PD".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(dev.rambally.statements.domain.exception.InvalidStatementException.class);
        PdfDocument pdf = new PdfDocument("%PDF-1.4".getBytes(StandardCharsets.US_ASCII));
        assertThat(pdf).isNotEqualTo(null).isNotEqualTo("%PDF-1.4");
        assertThat(pdf.toString()).isEqualTo("PdfDocument[8 bytes]");
    }

    @Test
    void account_number_and_customer_id_print_safely() {
        assertThat(new AccountNumber("1234567890").toString()).isEqualTo("******7890");
        assertThat(new CustomerId("C-1").toString()).isEqualTo("C-1");
        assertThat(StatementId.of("550e8400-e29b-41d4-a716-446655440000").toString()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(new StorageKey("2026/10/x.bin").toString()).isEqualTo("2026/10/x.bin");
    }
}
