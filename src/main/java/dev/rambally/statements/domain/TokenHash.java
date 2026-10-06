package dev.rambally.statements.domain;

import java.util.Arrays;
import java.util.HexFormat;

/** SHA-256 of a {@link LinkToken}; the only token-derived value that is ever stored or logged. */
public record TokenHash(byte[] value) {

    public TokenHash {
        Invariants.require(value != null && value.length == Sha256.LENGTH, "token hash must be 32 bytes");
        value = value.clone();
    }

    /** First eight hex characters: enough to correlate audit rows, useless for redemption. */
    public String prefix() {
        return HexFormat.of().formatHex(value, 0, 4);
    }

    @Override
    public byte[] value() {
        return value.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TokenHash other && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "TokenHash[" + prefix() + "...]";
    }
}
