package dev.rambally.statements.application.fakes;

import dev.rambally.statements.application.contract.DownloadLinkRepositoryContract;
import dev.rambally.statements.application.port.out.DownloadLinkRepository;
import dev.rambally.statements.application.port.out.StatementRepository;

class InMemoryDownloadLinkRepositoryTest extends DownloadLinkRepositoryContract {

    private InMemoryDownloadLinkRepository links = new InMemoryDownloadLinkRepository();
    private InMemoryStatementRepository statements = new InMemoryStatementRepository();

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
        links = new InMemoryDownloadLinkRepository();
        statements = new InMemoryStatementRepository();
    }
}
