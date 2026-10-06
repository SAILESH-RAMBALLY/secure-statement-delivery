package dev.rambally.statements.domain;

import java.util.Arrays;
import java.util.Objects;

/**
 * Everything needed to decrypt a statement except the key-encryption key: the format version, which
 * KEK wrapped the data key, the wrapped data key, and the two GCM nonces.
 */
public record EncryptionEnvelope(int cipherFormat, String kekId, byte[] wrappedDek, byte[] dekIv, byte[] contentIv) {

    public static final int FORMAT_AES_GCM_V1 = 1;
    public static final int WRAPPED_DEK_LENGTH = 48; // 32-byte key + 16-byte GCM tag
    public static final int IV_LENGTH = 12;

    public EncryptionEnvelope {
        Invariants.require(cipherFormat == FORMAT_AES_GCM_V1, "unsupported cipher format " + cipherFormat);
        Invariants.require(kekId != null && !kekId.isBlank(), "kek id must not be blank");
        Invariants.require(wrappedDek != null && wrappedDek.length == WRAPPED_DEK_LENGTH, "wrapped dek must be 48 bytes");
        Invariants.require(dekIv != null && dekIv.length == IV_LENGTH, "dek iv must be 12 bytes");
        Invariants.require(contentIv != null && contentIv.length == IV_LENGTH, "content iv must be 12 bytes");
        wrappedDek = wrappedDek.clone();
        dekIv = dekIv.clone();
        contentIv = contentIv.clone();
    }

    @Override
    public byte[] wrappedDek() {
        return wrappedDek.clone();
    }

    @Override
    public byte[] dekIv() {
        return dekIv.clone();
    }

    @Override
    public byte[] contentIv() {
        return contentIv.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EncryptionEnvelope other
                && cipherFormat == other.cipherFormat
                && kekId.equals(other.kekId)
                && Arrays.equals(wrappedDek, other.wrappedDek)
                && Arrays.equals(dekIv, other.dekIv)
                && Arrays.equals(contentIv, other.contentIv);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cipherFormat, kekId, Arrays.hashCode(wrappedDek), Arrays.hashCode(dekIv), Arrays.hashCode(contentIv));
    }

    @Override
    public String toString() {
        return "EncryptionEnvelope[format=" + cipherFormat + ", kekId=" + kekId + "]";
    }
}
