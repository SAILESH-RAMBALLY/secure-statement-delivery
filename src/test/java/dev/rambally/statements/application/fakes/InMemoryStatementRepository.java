package dev.rambally.statements.application.fakes;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.rambally.statements.application.port.out.StatementRepository;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.Statement;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.exception.DuplicateStatementException;

public final class InMemoryStatementRepository implements StatementRepository {

    private final Map<StatementId, Statement> byId = new ConcurrentHashMap<>();
    private RuntimeException failNextSaveWith;

    public void failNextSaveWith(RuntimeException e) {
        this.failNextSaveWith = e;
    }

    @Override
    public synchronized void save(Statement statement) {
        if (failNextSaveWith != null) {
            RuntimeException e = failNextSaveWith;
            failNextSaveWith = null;
            throw e;
        }
        boolean duplicate = byId.values().stream().anyMatch(s ->
                s.customerId().equals(statement.customerId())
                        && s.accountNumber().equals(statement.accountNumber())
                        && s.period().equals(statement.period()));
        if (duplicate || byId.containsKey(statement.id())) {
            throw new DuplicateStatementException(statement.customerId(), statement.accountNumber(), statement.period());
        }
        byId.put(statement.id(), statement);
    }

    @Override
    public Optional<Statement> findById(StatementId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<Statement> findByCustomer(CustomerId customerId) {
        return byId.values().stream()
                .filter(s -> s.customerId().equals(customerId))
                .sorted(Comparator.comparing((Statement s) -> s.period().value()).reversed()
                        .thenComparing(Comparator.comparing(Statement::createdAt).reversed()))
                .toList();
    }

    @Override
    public long count() {
        return byId.size();
    }
}
