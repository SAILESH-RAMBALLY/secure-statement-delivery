package dev.rambally.statements.application.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import dev.rambally.statements.adapters.out.crypto.LocalKekKeyProvider;
import dev.rambally.statements.application.port.out.KeyProvider;
import dev.rambally.statements.application.port.out.WrappedKey;
import dev.rambally.statements.domain.EncryptionEnvelope;
import dev.rambally.statements.domain.PdfDocument;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.exception.IntegrityException;

import org.junit.jupiter.api.Test;

class AesGcmEnvelopeCipherTest {

    private static final byte[] KEK_A = new byte[32];
    private static final byte[] KEK_B = new byte[32];

    static {
        for (int i = 0; i < 32; i++) {
            KEK_A[i] = (byte) i;
            KEK_B[i] = (byte) (255 - i);
        }
    }

    private final KeyProvider kekA = new LocalKekKeyProvider(KEK_A, "kek-a");
    private final KeyProvider kekB = new LocalKekKeyProvider(KEK_B, "kek-b");
    private final AesGcmEnvelopeCipher cipher = new AesGcmEnvelopeCipher();
    private final StatementId statementId = StatementId.newId();
    private final PdfDocument pdf = new PdfDocument("%PDF-1.4\nhello statement\n%%EOF".getBytes(StandardCharsets.US_ASCII));

    @Test
    void round_trips_pdf_bytes() {
        AesGcmEnvelopeCipher.Sealed sealed = cipher.seal(pdf, statementId, kekA);

        byte[] opened = cipher.open(sealed.ciphertext(), statementId, sealed.envelope(), kekA);

        assertThat(opened).isEqualTo(pdf.bytes());
        assertThat(sealed.envelope().kekId()).isEqualTo("kek-a");
        assertThat(sealed.envelope().cipherFormat()).isEqualTo(EncryptionEnvelope.FORMAT_AES_GCM_V1);
    }

    @Test
    void each_seal_uses_a_fresh_dek_and_iv() {
        Set<String> contentIvs = new HashSet<>();
        Set<String> wrappedDeks = new HashSet<>();
        Set<String> ciphertexts = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            AesGcmEnvelopeCipher.Sealed sealed = cipher.seal(pdf, statementId, kekA);
            contentIvs.add(java.util.HexFormat.of().formatHex(sealed.envelope().contentIv()));
            wrappedDeks.add(java.util.HexFormat.of().formatHex(sealed.envelope().wrappedDek()));
            ciphertexts.add(java.util.HexFormat.of().formatHex(sealed.ciphertext()));
        }
        assertThat(contentIvs).hasSize(100);
        assertThat(wrappedDeks).hasSize(100);
        assertThat(ciphertexts).hasSize(100);
    }

    @Test
    void ciphertext_length_is_plaintext_plus_sixteen() {
        AesGcmEnvelopeCipher.Sealed sealed = cipher.seal(pdf, statementId, kekA);

        assertThat(sealed.ciphertext()).hasSize(pdf.size() + 16);
        assertThat(new String(sealed.ciphertext(), StandardCharsets.ISO_8859_1)).doesNotContain("%PDF-");
    }

    @Test
    void tampered_ciphertext_byte_throws_integrity_failure() {
        AesGcmEnvelopeCipher.Sealed sealed = cipher.seal(pdf, statementId, kekA);
        byte[] tampered = sealed.ciphertext().clone();
        tampered[5] ^= 0x01;

        assertThatThrownBy(() -> cipher.open(tampered, statementId, sealed.envelope(), kekA))
                .isInstanceOf(IntegrityException.class);
    }

    @Test
    void ciphertext_opened_under_other_statement_id_fails_aad_check() {
        AesGcmEnvelopeCipher.Sealed sealed = cipher.seal(pdf, statementId, kekA);

        assertThatThrownBy(() -> cipher.open(sealed.ciphertext(), StatementId.newId(), sealed.envelope(), kekA))
                .isInstanceOf(IntegrityException.class);
    }

    @Test
    void tampered_wrapped_dek_fails_before_content_decrypt() {
        AesGcmEnvelopeCipher.Sealed sealed = cipher.seal(pdf, statementId, kekA);
        byte[] wrapped = sealed.envelope().wrappedDek();
        wrapped[0] ^= 0x01;
        EncryptionEnvelope tampered = new EncryptionEnvelope(1, sealed.envelope().kekId(), wrapped,
                sealed.envelope().dekIv(), sealed.envelope().contentIv());

        assertThatThrownBy(() -> cipher.open(sealed.ciphertext(), statementId, tampered, kekA))
                .isInstanceOf(IntegrityException.class);
    }

    @Test
    void opening_with_a_different_kek_fails() {
        AesGcmEnvelopeCipher.Sealed sealed = cipher.seal(pdf, statementId, kekA);

        assertThatThrownBy(() -> cipher.open(sealed.ciphertext(), statementId, sealed.envelope(), kekB))
                .isInstanceOf(IntegrityException.class);
    }

    @Test
    void rewrapping_dek_under_new_kek_id_still_opens_content() {
        // Key rotation re-wraps only the data key; the content ciphertext and its AAD are untouched.
        AesGcmEnvelopeCipher.Sealed sealed = cipher.seal(pdf, statementId, kekA);
        EncryptionEnvelope env = sealed.envelope();

        byte[] dek = kekA.unwrap(new WrappedKey(env.kekId(), env.wrappedDek(), env.dekIv()),
                AesGcmEnvelopeCipher.dekAad(statementId, env.kekId()));
        WrappedKey rewrapped = kekB.wrap(dek, AesGcmEnvelopeCipher.dekAad(statementId, kekB.currentKekId()));
        EncryptionEnvelope rotated = new EncryptionEnvelope(env.cipherFormat(), rewrapped.kekId(),
                rewrapped.wrappedDek(), rewrapped.dekIv(), env.contentIv());

        byte[] opened = cipher.open(sealed.ciphertext(), statementId, rotated, kekB);

        assertThat(opened).isEqualTo(pdf.bytes());
        assertThat(rotated.kekId()).isEqualTo("kek-b");
    }

    @Test
    void a_key_provider_returning_an_unusable_key_is_a_programming_error_not_an_integrity_failure() {
        AesGcmEnvelopeCipher.Sealed sealed = cipher.seal(pdf, statementId, kekA);
        KeyProvider broken = new KeyProvider() {
            @Override
            public String currentKekId() {
                return "kek-a";
            }

            @Override
            public WrappedKey wrap(byte[] dek, byte[] aad) {
                return kekA.wrap(dek, aad);
            }

            @Override
            public byte[] unwrap(WrappedKey wrapped, byte[] aad) {
                return new byte[15]; // not a valid AES key length
            }
        };

        assertThatThrownBy(() -> cipher.open(sealed.ciphertext(), statementId, sealed.envelope(), broken))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(IntegrityException.class);
    }

    @Test
    void sealed_result_copies_its_ciphertext() {
        AesGcmEnvelopeCipher.Sealed sealed = cipher.seal(pdf, statementId, kekA);
        byte[] first = sealed.ciphertext();
        first[0] ^= 0x01;

        assertThat(sealed.ciphertext()[0]).isNotEqualTo(first[0]);
        assertThat(sealed.envelope().toString()).doesNotContain("wrappedDek");
    }
}
