package dev.rambally.statements.application.port.out;

/** A data-encryption key wrapped (encrypted) by the key-encryption key identified by {@code kekId}. */
public record WrappedKey(String kekId, byte[] wrappedDek, byte[] dekIv) {

    public WrappedKey {
        if (kekId == null || kekId.isBlank()) {
            throw new IllegalArgumentException("kek id must not be blank");
        }
        if (wrappedDek == null || dekIv == null) {
            throw new IllegalArgumentException("wrapped key material must not be null");
        }
        wrappedDek = wrappedDek.clone();
        dekIv = dekIv.clone();
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
    public String toString() {
        return "WrappedKey[kekId=" + kekId + "]";
    }
}
