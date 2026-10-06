package dev.rambally.statements.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

public record Sha256(byte[] value) {

    public static final int LENGTH = 32;

    public Sha256 {
        Invariants.require(value != null && value.length == LENGTH, "sha-256 must be 32 bytes");
        value = value.clone();
    }

    public static Sha256 of(byte[] input) {
        try {
            return new Sha256(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory in every JRE", e);
        }
    }

    public String hex() {
        return HexFormat.of().formatHex(value);
    }

    @Override
    public byte[] value() {
        return value.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Sha256 other && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "Sha256[" + hex() + "]";
    }
}
