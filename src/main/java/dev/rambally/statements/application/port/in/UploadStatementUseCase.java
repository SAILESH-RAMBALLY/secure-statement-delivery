package dev.rambally.statements.application.port.in;

import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.exception.DuplicateStatementException;
import dev.rambally.statements.domain.exception.ForbiddenException;
import dev.rambally.statements.domain.exception.InvalidStatementException;

/** Store a customer's statement PDF, encrypted at rest. Administrators only. */
public interface UploadStatementUseCase {

    /**
     * @throws ForbiddenException          if the actor is not an administrator
     * @throws InvalidStatementException   if the bytes are not an acceptable PDF or the period is in the future
     * @throws DuplicateStatementException if a statement for this customer, account and period exists
     */
    StatementId upload(UploadStatementCommand command);
}
