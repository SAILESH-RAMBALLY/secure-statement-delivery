package dev.rambally.statements.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import dev.rambally.statements.domain.exception.InvalidStatementException;

/** Plaintext PDF bytes. Only the magic header is checked here; the size policy belongs to the use case. */
public record PdfDocument(byte[] bytes) {

    private static final byte[] MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    public PdfDocument {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidStatementException(InvalidStatementException.Reason.EMPTY);
        }
        if (!startsWithMagic(bytes)) {
            throw new InvalidStatementException(InvalidStatementException.Reason.NOT_PDF);
        }
        bytes = bytes.clone();
    }

    private static boolean startsWithMagic(byte[] bytes) {
        return bytes.length >= MAGIC.length && Arrays.equals(bytes, 0, MAGIC.length, MAGIC, 0, MAGIC.length);
    }

    public int size() {
        return bytes.length;
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PdfDocument other && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "PdfDocument[" + bytes.length + " bytes]";
    }
}
