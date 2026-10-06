package dev.rambally.statements.application.port.in;

import dev.rambally.statements.domain.exception.LinkNotRedeemableException;

/**
 * Exchange a download token for the decrypted statement, consuming one use of the link atomically.
 * Takes the raw path segment because parsing untrusted input (and auditing a malformed one) is part of
 * the use case, not of the HTTP adapter.
 */
public interface RedeemDownloadLinkUseCase {

    /** @throws LinkNotRedeemableException for every failure; the outcome inside is for the audit trail only */
    StatementDownload redeem(String rawToken, RequestContext context);
}
