package dev.rambally.statements.adapters.out.persistence;

import dev.rambally.statements.application.contract.StatementRepositoryContract;
import dev.rambally.statements.application.port.out.StatementRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@JdbcSliceTest
@Import(JdbcStatementRepository.class)
class H2StatementRepositoryTest extends StatementRepositoryContract {

    @Autowired
    private JdbcStatementRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @Override
    protected StatementRepository repository() {
        return repository;
    }

    @Override
    protected void reset() {
        TableCleaner.deleteAll(jdbc);
    }
}
