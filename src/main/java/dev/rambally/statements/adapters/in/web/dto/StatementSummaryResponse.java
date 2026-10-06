package dev.rambally.statements.adapters.in.web.dto;

import java.time.Instant;

import dev.rambally.statements.application.port.in.StatementSummary;

public record StatementSummaryResponse(String statementId, String accountNumber, String period, long sizeBytes,
        Instant createdAt) {

    public static StatementSummaryResponse of(StatementSummary s) {
        return new StatementSummaryResponse(s.statementId().toString(), s.maskedAccountNumber(), s.period().toString(),
                s.sizeBytes(), s.createdAt());
    }
}
