package dev.rambally.statements.application.fakes;

import dev.rambally.statements.application.contract.StatementRepositoryContract;
import dev.rambally.statements.application.port.out.StatementRepository;

class InMemoryStatementRepositoryTest extends StatementRepositoryContract {

    private InMemoryStatementRepository repository = new InMemoryStatementRepository();

    @Override
    protected StatementRepository repository() {
        return repository;
    }

    @Override
    protected void reset() {
        repository = new InMemoryStatementRepository();
    }
}
