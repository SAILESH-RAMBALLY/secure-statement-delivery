package dev.rambally.statements.support;

import java.nio.charset.StandardCharsets;

/**
 * A hand-written, structurally valid single-page PDF (~400 bytes). Used by every unit, slice and
 * end-to-end test so the fast suite never loads a PDF library.
 */
public final class TestPdfs {

    private static final String MINIMAL = """
            %PDF-1.4
            1 0 obj
            << /Type /Catalog /Pages 2 0 R >>
            endobj
            2 0 obj
            << /Type /Pages /Kids [3 0 R] /Count 1 >>
            endobj
            3 0 obj
            << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << >> >>
            endobj
            xref
            0 4
            0000000000 65535 f\s
            0000000009 00000 n\s
            0000000058 00000 n\s
            0000000115 00000 n\s
            trailer
            << /Size 4 /Root 1 0 R >>
            startxref
            208
            %%EOF
            """;

    private TestPdfs() {
    }

    public static byte[] minimal() {
        return MINIMAL.getBytes(StandardCharsets.US_ASCII);
    }

    /** A valid PDF padded with comment bytes to the requested total size. */
    public static byte[] ofSize(int size) {
        byte[] base = minimal();
        if (size < base.length) {
            throw new IllegalArgumentException("size must be at least " + base.length);
        }
        byte[] out = new byte[size];
        System.arraycopy(base, 0, out, 0, base.length);
        for (int i = base.length; i < size; i++) {
            out[i] = (byte) (i % 7 == 0 ? '\n' : '%');
        }
        return out;
    }
}
