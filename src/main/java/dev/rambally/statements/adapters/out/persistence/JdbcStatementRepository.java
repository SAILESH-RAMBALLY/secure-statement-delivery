package dev.rambally.statements.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.rambally.statements.application.port.out.StatementRepository;
import dev.rambally.statements.domain.AccountNumber;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.EncryptionEnvelope;
import dev.rambally.statements.domain.Sha256;
import dev.rambally.statements.domain.Statement;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.StatementPeriod;
import dev.rambally.statements.domain.StorageKey;
import dev.rambally.statements.domain.exception.DuplicateStatementException;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Plain SQL via JdbcClient; the domain record is built straight from the ResultSet. */
@Repository
public class JdbcStatementRepository implements StatementRepository {

    private static final String COLUMNS = "id, customer_id, account_number, period, size_bytes, content_sha256, "
            + "storage_key, cipher_format, kek_id, wrapped_dek, dek_iv, content_iv, created_at";

    private final JdbcClient jdbc;

    public JdbcStatementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Statement s) {
        try {
            jdbc.sql("INSERT INTO statement (" + COLUMNS + ") VALUES (:id, :customer_id, :account_number, :period, "
                            + ":size_bytes, :content_sha256, :storage_key, :cipher_format, :kek_id, :wrapped_dek, :dek_iv, "
                            + ":content_iv, :created_at)")
                    .param("id", s.id().value())
                    .param("customer_id", s.customerId().value())
                    .param("account_number", s.accountNumber().value())
                    .param("period", s.period().toString())
                    .param("size_bytes", s.sizeBytes())
                    .param("content_sha256", s.contentHash().value())
                    .param("storage_key", s.storageKey().value())
                    .param("cipher_format", s.envelope().cipherFormat())
                    .param("kek_id", s.envelope().kekId())
                    .param("wrapped_dek", s.envelope().wrappedDek())
                    .param("dek_iv", s.envelope().dekIv())
                    .param("content_iv", s.envelope().contentIv())
                    .param("created_at", JdbcTimes.toDb(s.createdAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw new DuplicateStatementException(s.customerId(), s.accountNumber(), s.period(), e);
        }
    }

    @Override
    public Optional<Statement> findById(StatementId id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM statement WHERE id = :id")
                .param("id", id.value())
                .query((rs, rowNum) -> toStatement(rs))
                .optional();
    }

    @Override
    public List<Statement> findByCustomer(CustomerId customerId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM statement WHERE customer_id = :customer_id "
                        + "ORDER BY period DESC, created_at DESC")
                .param("customer_id", customerId.value())
                .query((rs, rowNum) -> toStatement(rs))
                .list();
    }

    @Override
    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM statement").query(Long.class).single();
    }

    private static Statement toStatement(ResultSet rs) throws SQLException {
        return new Statement(
                new StatementId(rs.getObject("id", UUID.class)),
                new CustomerId(rs.getString("customer_id")),
                new AccountNumber(rs.getString("account_number")),
                StatementPeriod.parse(rs.getString("period").trim()),
                rs.getLong("size_bytes"),
                new Sha256(rs.getBytes("content_sha256")),
                new StorageKey(rs.getString("storage_key")),
                new EncryptionEnvelope(rs.getInt("cipher_format"), rs.getString("kek_id"), rs.getBytes("wrapped_dek"),
                        rs.getBytes("dek_iv"), rs.getBytes("content_iv")),
                JdbcTimes.fromDb(rs, "created_at"));
    }
}
