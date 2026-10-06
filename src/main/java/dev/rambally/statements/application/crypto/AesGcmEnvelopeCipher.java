package dev.rambally.statements.application.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import dev.rambally.statements.application.port.out.KeyProvider;
import dev.rambally.statements.application.port.out.WrappedKey;
import dev.rambally.statements.domain.EncryptionEnvelope;
import dev.rambally.statements.domain.PdfDocument;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.exception.IntegrityException;

/**
 * Envelope encryption for statement PDFs.
 *
 * <ul>
 *   <li>A fresh 256-bit data key (DEK) and 96-bit nonce per statement, so nonce reuse is impossible by construction.</li>
 *   <li>Content AAD binds the ciphertext to the statement id and format version. It deliberately excludes the
 *       KEK id, so rotating the KEK (re-wrapping the DEK) never invalidates stored ciphertext.</li>
 *   <li>DEK-wrap AAD binds the wrapped key to the statement id and the KEK that wrapped it.</li>
 * </ul>
 * Pure JCE; no framework types.
 */
public final class AesGcmEnvelopeCipher {

    public record Sealed(byte[] ciphertext, EncryptionEnvelope envelope) {
        public Sealed {
            ciphertext = ciphertext.clone();
        }

        @Override
        public byte[] ciphertext() {
            return ciphertext.clone();
        }
    }

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int DEK_BYTES = 32;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();

    public Sealed seal(PdfDocument pdf, StatementId statementId, KeyProvider keyProvider) {
        byte[] dek = randomBytes(DEK_BYTES);
        byte[] contentIv = randomBytes(EncryptionEnvelope.IV_LENGTH);
        try {
            byte[] ciphertext = gcm(Cipher.ENCRYPT_MODE, dek, contentIv, contentAad(statementId), pdf.bytes());
            String kekId = keyProvider.currentKekId();
            WrappedKey wrapped = keyProvider.wrap(dek, dekAad(statementId, kekId));
            EncryptionEnvelope envelope = new EncryptionEnvelope(EncryptionEnvelope.FORMAT_AES_GCM_V1,
                    wrapped.kekId(), wrapped.wrappedDek(), wrapped.dekIv(), contentIv);
            return new Sealed(ciphertext, envelope);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        } finally {
            java.util.Arrays.fill(dek, (byte) 0);
        }
    }

    /** @throws IntegrityException if the wrapped key or the content fails authentication. */
    public byte[] open(byte[] ciphertext, StatementId statementId, EncryptionEnvelope envelope, KeyProvider keyProvider) {
        byte[] dek = keyProvider.unwrap(new WrappedKey(envelope.kekId(), envelope.wrappedDek(), envelope.dekIv()),
                dekAad(statementId, envelope.kekId()));
        try {
            return gcm(Cipher.DECRYPT_MODE, dek, envelope.contentIv(), contentAad(statementId), ciphertext);
        } catch (AEADBadTagException e) {
            throw new IntegrityException("statement content failed authentication");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        } finally {
            java.util.Arrays.fill(dek, (byte) 0);
        }
    }

    static byte[] contentAad(StatementId statementId) {
        return ("stmt:" + statementId + ":fmt:" + EncryptionEnvelope.FORMAT_AES_GCM_V1).getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] dekAad(StatementId statementId, String kekId) {
        return ("dek:" + statementId + ":kek:" + kekId).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] gcm(int mode, byte[] key, byte[] iv, byte[] aad, byte[] input) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(input);
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }
}
