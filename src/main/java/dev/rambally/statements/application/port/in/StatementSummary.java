package dev.rambally.statements.application.port.in;

import java.time.Instant;

import dev.rambally.statements.domain.Statement;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.StatementPeriod;

public record StatementSummary(StatementId statementId, String maskedAccountNumber, StatementPeriod period,
        long sizeBytes, Instant createdAt) {

    public static StatementSummary of(Statement statement) {
        return new StatementSummary(statement.id(), statement.accountNumber().masked(), statement.period(),
                statement.sizeBytes(), statement.createdAt());
    }
}
