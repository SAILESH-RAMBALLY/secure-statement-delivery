package dev.rambally.statements.application;

import dev.rambally.statements.domain.PdfDocument;
import dev.rambally.statements.domain.exception.InvalidStatementException;

/**
 * Upper bound on statement size. It is what makes bounded-buffer decryption safe: a download can never
 * allocate more than this plus a GCM tag.
 */
public record StatementSizePolicy(long maxBytes) {

    public StatementSizePolicy {
        if (maxBytes < 1024) {
            throw new IllegalArgumentException("max statement size must be at least 1 KiB");
        }
    }

    public void check(PdfDocument pdf) {
        if (pdf.size() > maxBytes) {
            throw new InvalidStatementException(InvalidStatementException.Reason.TOO_LARGE);
        }
    }
}
