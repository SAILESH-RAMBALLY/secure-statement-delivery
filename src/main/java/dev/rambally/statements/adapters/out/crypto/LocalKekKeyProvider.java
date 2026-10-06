package dev.rambally.statements.adapters.out.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import dev.rambally.statements.application.port.out.KeyProvider;
import dev.rambally.statements.application.port.out.WrappedKey;
import dev.rambally.statements.domain.exception.IntegrityException;

/**
 * Key-encryption key held in process memory, loaded from configuration. The production path is a
 * KMS/HSM-backed implementation of the same port; this adapter exists so the service runs anywhere.
 */
public final class LocalKekKeyProvider implements KeyProvider {

    /** Well-known development key. Refused by the prod profile guard; used only when fallback is explicitly allowed. */
    public static final String DEV_KEK_BASE64 = "ZGV2LW9ubHkta2VrLW5vdC1mb3ItcHJvZHVjdGlvbiE=";

    private static final int KEK_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec kek;
    private final String kekId;
    private final boolean devFallback;
    private final SecureRandom random = new SecureRandom();

    public LocalKekKeyProvider(byte[] kek, String kekId) {
        this(kek, kekId, false);
    }

    private LocalKekKeyProvider(byte[] kek, String kekId, boolean devFallback) {
        if (kek == null || kek.length != KEK_BYTES) {
            throw new IllegalArgumentException("KEK must be exactly 32 bytes");
        }
        if (kekId == null || kekId.isBlank()) {
            throw new IllegalArgumentException("KEK id must not be blank");
        }
        this.kek = new SecretKeySpec(kek, "AES");
        this.kekId = kekId;
        this.devFallback = devFallback;
    }

    /**
     * Builds the provider from configuration. A blank value is treated as absent (compose substitutes an
     * empty string for an unset variable) and falls back to the development key only when allowed.
     */
    public static LocalKekKeyProvider fromConfig(String base64Kek, String kekId, boolean devFallbackAllowed) {
        boolean blank = base64Kek == null || base64Kek.isBlank();
        if (blank && !devFallbackAllowed) {
            throw new IllegalArgumentException("APP_CRYPTO_KEK is required (base64 of 32 random bytes)");
        }
        String effective = blank ? DEV_KEK_BASE64 : base64Kek.trim();
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(effective);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("APP_CRYPTO_KEK must be valid base64");
        }
        boolean isDevKey = Arrays.equals(bytes, Base64.getDecoder().decode(DEV_KEK_BASE64));
        return new LocalKekKeyProvider(bytes, kekId, isDevKey);
    }

    /** True when the well-known development key is in use, whether by fallback or by explicit configuration. */
    public boolean usingDevFallback() {
        return devFallback;
    }

    @Override
    public String currentKekId() {
        return kekId;
    }

    @Override
    public WrappedKey wrap(byte[] dek, byte[] aad) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            return new WrappedKey(kekId, gcm(Cipher.ENCRYPT_MODE, iv, aad, dek), iv);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("DEK wrap failed", e);
        }
    }

    @Override
    public byte[] unwrap(WrappedKey wrapped, byte[] aad) {
        if (!kekId.equals(wrapped.kekId())) {
            throw new IntegrityException("wrapped key belongs to KEK " + wrapped.kekId() + ", this provider holds " + kekId);
        }
        try {
            return gcm(Cipher.DECRYPT_MODE, wrapped.dekIv(), aad, wrapped.wrappedDek());
        } catch (AEADBadTagException e) {
            throw new IntegrityException("wrapped data key failed authentication");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("DEK unwrap failed", e);
        }
    }

    private byte[] gcm(int mode, byte[] iv, byte[] aad, byte[] input) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, kek, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(input);
    }
}
