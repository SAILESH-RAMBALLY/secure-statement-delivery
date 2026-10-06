package dev.rambally.statements.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import dev.rambally.statements.domain.exception.InvalidStatementException;

/** The calendar month a statement covers. Bank statements are monthly, so a YearMonth is the whole story. */
public record StatementPeriod(YearMonth value) {

    public StatementPeriod {
        Invariants.notNull(value, "statement period");
    }

    /** Parses {@code yyyy-MM}; anything else is a {@link InvalidStatementException.Reason#BAD_PERIOD}. */
    public static StatementPeriod parse(String text) {
        if (text == null) {
            throw new InvalidStatementException(InvalidStatementException.Reason.BAD_PERIOD);
        }
        try {
            return new StatementPeriod(YearMonth.parse(text));
        } catch (DateTimeParseException e) {
            throw new InvalidStatementException(InvalidStatementException.Reason.BAD_PERIOD);
        }
    }

    /** True when this period lies after the UTC month containing {@code instant}. */
    public boolean isAfterMonthOf(Instant instant) {
        return value.isAfter(YearMonth.from(instant.atOffset(ZoneOffset.UTC)));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
