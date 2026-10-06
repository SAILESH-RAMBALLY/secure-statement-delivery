package dev.rambally.statements.application.port.out;

import java.util.List;
import java.util.Optional;

import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.Statement;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.exception.DuplicateStatementException;

public interface StatementRepository {

    /** @throws DuplicateStatementException when (customer, account, period) already exists */
    void save(Statement statement);

    Optional<Statement> findById(StatementId id);

    /** Newest period first. */
    List<Statement> findByCustomer(CustomerId customerId);

    long count();
}
