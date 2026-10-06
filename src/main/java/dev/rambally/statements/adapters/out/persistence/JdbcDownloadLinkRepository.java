package dev.rambally.statements.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.rambally.statements.application.port.out.DownloadLinkRepository;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.DownloadLink;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.TokenHash;
import dev.rambally.statements.domain.exception.DuplicateTokenHashException;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDownloadLinkRepository implements DownloadLinkRepository {

    private static final String COLUMNS = "id, statement_id, customer_id, token_hash, issued_at, expires_at, "
            + "max_downloads, download_count, revoked_at";

    private final JdbcClient jdbc;

    public JdbcDownloadLinkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(DownloadLink link) {
        try {
            jdbc.sql("INSERT INTO download_link (" + COLUMNS + ") VALUES (:id, :statement_id, :customer_id, :token_hash, "
                            + ":issued_at, :expires_at, :max_downloads, :download_count, :revoked_at)")
                    .param("id", link.id().value())
                    .param("statement_id", link.statementId().value())
                    .param("customer_id", link.customerId().value())
                    .param("token_hash", link.tokenHash().value())
                    .param("issued_at", JdbcTimes.toDb(link.issuedAt()))
                    .param("expires_at", JdbcTimes.toDb(link.expiresAt()))
                    .param("max_downloads", link.maxDownloads())
                    .param("download_count", link.downloadCount())
                    .param("revoked_at", JdbcTimes.toDb(link.revokedAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw new DuplicateTokenHashException();
        }
    }

    @Override
    public Optional<DownloadLink> findByTokenHash(TokenHash hash) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM download_link WHERE token_hash = :token_hash")
                .param("token_hash", hash.value())
                .query((rs, rowNum) -> toLink(rs))
                .optional();
    }

    @Override
    public Optional<DownloadLink> findById(LinkId id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM download_link WHERE id = :id")
                .param("id", id.value())
                .query((rs, rowNum) -> toLink(rs))
                .optional();
    }

    @Override
    public List<DownloadLink> findByStatement(StatementId statementId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM download_link WHERE statement_id = :statement_id ORDER BY issued_at DESC")
                .param("statement_id", statementId.value())
                .query((rs, rowNum) -> toLink(rs))
                .list();
    }

    @Override
    public boolean tryConsume(LinkId id, Instant now) {
        int updated = jdbc.sql("UPDATE download_link SET download_count = download_count + 1, last_downloaded_at = :now "
                        + "WHERE id = :id AND revoked_at IS NULL AND expires_at > :now AND download_count < max_downloads")
                .param("id", id.value())
                .param("now", JdbcTimes.toDb(now))
                .update();
        return updated == 1;
    }

    @Override
    public boolean revoke(LinkId id, Instant now) {
        int updated = jdbc.sql("UPDATE download_link SET revoked_at = :now WHERE id = :id AND revoked_at IS NULL")
                .param("id", id.value())
                .param("now", JdbcTimes.toDb(now))
                .update();
        return updated == 1;
    }

    private static DownloadLink toLink(ResultSet rs) throws SQLException {
        return new DownloadLink(
                new LinkId(rs.getObject("id", UUID.class)),
                new StatementId(rs.getObject("statement_id", UUID.class)),
                new CustomerId(rs.getString("customer_id")),
                new TokenHash(rs.getBytes("token_hash")),
                JdbcTimes.fromDb(rs, "issued_at"),
                JdbcTimes.fromDb(rs, "expires_at"),
                rs.getInt("max_downloads"),
                rs.getInt("download_count"),
                JdbcTimes.fromDb(rs, "revoked_at"));
    }
}
