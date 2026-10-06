package dev.rambally.statements.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import dev.rambally.statements.domain.exception.InvalidStatementException;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValueObjectsTest {

    @Nested
    class CustomerIdTest {

        @ParameterizedTest
        @ValueSource(strings = {"C-1001", "auth0|abc123", "550e8400-e29b-41d4-a716-446655440000", "user@example.com"})
        void accepts_realistic_identity_provider_subjects(String value) {
            assertThat(new CustomerId(value).value()).isEqualTo(value);
        }

        @Test
        void rejects_blank_too_long_and_control_characters() {
            assertThatThrownBy(() -> new CustomerId("  ")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new CustomerId(null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new CustomerId("x".repeat(129))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new CustomerId("bad\nid")).isInstanceOf(IllegalArgumentException.class);
            assertThat(new CustomerId("x".repeat(128)).value()).hasSize(128);
        }
    }

    @Nested
    class AccountNumberTest {

        @Test
        void requires_6_to_20_digits() {
            assertThat(new AccountNumber("123456").value()).isEqualTo("123456");
            assertThat(new AccountNumber("1".repeat(20)).value()).hasSize(20);
            assertThatThrownBy(() -> new AccountNumber("12345")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AccountNumber("1".repeat(21))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AccountNumber("12345a7890")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AccountNumber(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void masks_all_but_the_last_four_digits() {
            assertThat(new AccountNumber("1234567890").masked()).isEqualTo("******7890");
        }
    }

    @Nested
    class StatementIdTest {

        @Test
        void new_ids_are_unique_and_round_trip_through_string() {
            StatementId a = StatementId.newId();
            StatementId b = StatementId.newId();
            assertThat(a).isNotEqualTo(b);
            assertThat(StatementId.of(a.toString())).isEqualTo(a);
            assertThatThrownBy(() -> StatementId.of("not-a-uuid")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new StatementId(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class StatementPeriodTest {

        @Test
        void parses_yyyy_MM_and_rejects_garbage_with_bad_period_reason() {
            assertThat(StatementPeriod.parse("2026-09").value()).isEqualTo(YearMonth.of(2026, 9));
            assertThat(StatementPeriod.parse("2026-09").toString()).isEqualTo("2026-09");
            assertThatThrownBy(() -> StatementPeriod.parse("September 2026"))
                    .isInstanceOf(InvalidStatementException.class)
                    .extracting(e -> ((InvalidStatementException) e).reason())
                    .isEqualTo(InvalidStatementException.Reason.BAD_PERIOD);
            assertThatThrownBy(() -> StatementPeriod.parse("2026-13"))
                    .isInstanceOf(InvalidStatementException.class);
            assertThatThrownBy(() -> StatementPeriod.parse(null))
                    .isInstanceOf(InvalidStatementException.class);
        }

        @Test
        void knows_whether_it_is_after_a_given_instant() {
            Instant now = Instant.parse("2026-10-06T10:00:00Z");
            assertThat(StatementPeriod.parse("2026-10").isAfterMonthOf(now)).isFalse();
            assertThat(StatementPeriod.parse("2026-11").isAfterMonthOf(now)).isTrue();
            assertThat(StatementPeriod.parse("2026-09").isAfterMonthOf(now)).isFalse();
        }
    }

    @Nested
    class PdfDocumentTest {

        @Test
        void accepts_pdf_magic_bytes() {
            byte[] bytes = "%PDF-1.4\n...".getBytes(StandardCharsets.US_ASCII);
            PdfDocument pdf = new PdfDocument(bytes);
            assertThat(pdf.size()).isEqualTo(bytes.length);
            assertThat(pdf.bytes()).isEqualTo(bytes).isNotSameAs(bytes);
        }

        @Test
        void rejects_empty_with_EMPTY_and_non_pdf_with_NOT_PDF() {
            assertThatThrownBy(() -> new PdfDocument(new byte[0]))
                    .isInstanceOf(InvalidStatementException.class)
                    .extracting(e -> ((InvalidStatementException) e).reason())
                    .isEqualTo(InvalidStatementException.Reason.EMPTY);
            assertThatThrownBy(() -> new PdfDocument(null))
                    .isInstanceOf(InvalidStatementException.class);
            assertThatThrownBy(() -> new PdfDocument("<html>".getBytes(StandardCharsets.US_ASCII)))
                    .isInstanceOf(InvalidStatementException.class)
                    .extracting(e -> ((InvalidStatementException) e).reason())
                    .isEqualTo(InvalidStatementException.Reason.NOT_PDF);
        }

        @Test
        void equality_is_by_content() {
            byte[] bytes = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
            assertThat(new PdfDocument(bytes)).isEqualTo(new PdfDocument(bytes.clone()))
                    .hasSameHashCodeAs(new PdfDocument(bytes.clone()));
        }
    }

    @Nested
    class Sha256Test {

        @Test
        void hashes_bytes_to_a_known_answer_and_formats_hex() {
            Sha256 hash = Sha256.of("abc".getBytes(StandardCharsets.US_ASCII));
            assertThat(hash.hex()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
            assertThat(hash.value()).hasSize(32);
        }

        @Test
        void equality_is_by_content_and_requires_32_bytes() {
            byte[] a = Sha256.of(new byte[] {1}).value();
            assertThat(new Sha256(a)).isEqualTo(new Sha256(a.clone())).hasSameHashCodeAs(new Sha256(a.clone()));
            assertThatThrownBy(() -> new Sha256(new byte[31])).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class StorageKeyTest {

        @Test
        void derives_year_month_and_id_from_statement_id_and_creation_time() {
            StatementId id = StatementId.of("550e8400-e29b-41d4-a716-446655440000");
            StorageKey key = StorageKey.of(id, Instant.parse("2026-09-30T23:59:59Z"));
            assertThat(key.value()).isEqualTo("2026/09/550e8400-e29b-41d4-a716-446655440000.bin");
        }

        @ParameterizedTest
        @ValueSource(strings = {"../etc/passwd", "/abs/path.bin", "2026\\09\\x.bin", "", "a/../b.bin"})
        void rejects_path_traversal_absolute_paths_and_backslashes(String value) {
            assertThatThrownBy(() -> new StorageKey(value)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class EncryptionEnvelopeTest {

        @Test
        void validates_lengths_and_copies_arrays() {
            byte[] wrapped = new byte[48];
            byte[] dekIv = new byte[12];
            byte[] contentIv = new byte[12];
            EncryptionEnvelope env = new EncryptionEnvelope(1, "kek-v1", wrapped, dekIv, contentIv);
            wrapped[0] = 42;
            assertThat(env.wrappedDek()[0]).isZero();
            assertThat(env.wrappedDek()).isNotSameAs(wrapped);

            assertThatThrownBy(() -> new EncryptionEnvelope(2, "kek-v1", new byte[48], new byte[12], new byte[12]))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new EncryptionEnvelope(1, " ", new byte[48], new byte[12], new byte[12]))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new EncryptionEnvelope(1, "kek-v1", new byte[47], new byte[12], new byte[12]))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new EncryptionEnvelope(1, "kek-v1", new byte[48], new byte[16], new byte[12]))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new EncryptionEnvelope(1, "kek-v1", new byte[48], new byte[12], new byte[11]))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void equality_is_by_content() {
            EncryptionEnvelope a = new EncryptionEnvelope(1, "kek-v1", new byte[48], new byte[12], new byte[12]);
            EncryptionEnvelope b = new EncryptionEnvelope(1, "kek-v1", new byte[48], new byte[12], new byte[12]);
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }
    }

    @Nested
    class LinkIdTest {

        @Test
        void round_trips_through_string() {
            LinkId id = LinkId.newId();
            assertThat(LinkId.of(id.toString())).isEqualTo(id);
            assertThat(id.value()).isInstanceOf(UUID.class);
        }
    }
}
