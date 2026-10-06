package dev.rambally.statements.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Instants cross the JDBC boundary as UTC OffsetDateTime: the PostgreSQL driver cannot bind or read
 * {@link Instant} directly, while both PostgreSQL and H2 handle OffsetDateTime.
 */
final class JdbcTimes {

    private JdbcTimes() {
    }

    static OffsetDateTime toDb(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    static Instant fromDb(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
