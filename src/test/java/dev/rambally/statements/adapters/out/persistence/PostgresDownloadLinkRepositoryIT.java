package dev.rambally.statements.adapters.out.persistence;

import dev.rambally.statements.application.contract.DownloadLinkRepositoryContract;
import dev.rambally.statements.application.port.out.DownloadLinkRepository;
import dev.rambally.statements.application.port.out.StatementRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgresSliceIT
@Import({JdbcStatementRepository.class, JdbcDownloadLinkRepository.class})
class PostgresDownloadLinkRepositoryIT extends DownloadLinkRepositoryContract {

    @Autowired
    private JdbcDownloadLinkRepository links;

    @Autowired
    private JdbcStatementRepository statements;

    @Autowired
    private JdbcClient jdbc;

    @Override
    protected DownloadLinkRepository links() {
        return links;
    }

    @Override
    protected StatementRepository statements() {
        return statements;
    }

    @Override
    protected void reset() {
        TableCleaner.deleteAll(jdbc);
    }
}
