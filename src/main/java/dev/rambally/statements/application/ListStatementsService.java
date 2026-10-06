package dev.rambally.statements.application;

import java.util.List;

import dev.rambally.statements.application.port.in.ListStatementsUseCase;
import dev.rambally.statements.application.port.in.StatementSummary;
import dev.rambally.statements.application.port.out.StatementRepository;

public final class ListStatementsService implements ListStatementsUseCase {

    private final StatementRepository statements;

    public ListStatementsService(StatementRepository statements) {
        this.statements = statements;
    }

    @Override
    public List<StatementSummary> listFor(Principal actor) {
        return statements.findByCustomer(actor.customerId()).stream().map(StatementSummary::of).toList();
    }
}
