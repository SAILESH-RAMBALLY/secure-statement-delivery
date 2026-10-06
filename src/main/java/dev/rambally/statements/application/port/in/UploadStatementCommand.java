package dev.rambally.statements.application.port.in;

import dev.rambally.statements.application.Principal;
import dev.rambally.statements.domain.AccountNumber;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.StatementPeriod;

/**
 * The customer id here describes whose statement this is; the actor is the administrator uploading it.
 * A transfer object: the caller hands over the byte array and must not reuse it.
 */
public record UploadStatementCommand(
        CustomerId customerId,
        AccountNumber accountNumber,
        StatementPeriod period,
        byte[] pdfBytes,
        Principal actor) {

    public UploadStatementCommand {
        if (customerId == null || accountNumber == null || period == null || actor == null) {
            throw new IllegalArgumentException("upload command is incomplete");
        }
        pdfBytes = pdfBytes == null ? new byte[0] : pdfBytes;
    }
}
