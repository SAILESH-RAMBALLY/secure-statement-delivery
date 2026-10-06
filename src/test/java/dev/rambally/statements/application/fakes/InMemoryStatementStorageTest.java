package dev.rambally.statements.application.fakes;

import dev.rambally.statements.application.contract.StatementStorageContract;
import dev.rambally.statements.application.port.out.StatementStorage;

class InMemoryStatementStorageTest extends StatementStorageContract {

    private final InMemoryStatementStorage storage = new InMemoryStatementStorage();

    @Override
    protected StatementStorage storage() {
        return storage;
    }
}
