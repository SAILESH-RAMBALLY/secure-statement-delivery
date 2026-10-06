package dev.rambally.statements.application.fakes;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.rambally.statements.application.port.out.DownloadLinkRepository;
import dev.rambally.statements.domain.DownloadLink;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.Redeemability;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.TokenHash;
import dev.rambally.statements.domain.exception.DuplicateTokenHashException;

public final class InMemoryDownloadLinkRepository implements DownloadLinkRepository {

    private final Map<LinkId, DownloadLink> byId = new ConcurrentHashMap<>();
    private volatile boolean failNextConsume;

    /** Test hook: make the next tryConsume lose the race even though the pre-check passed. */
    public void failNextConsume() {
        this.failNextConsume = true;
    }

    @Override
    public synchronized void save(DownloadLink link) {
        boolean duplicateHash = byId.values().stream()
                .anyMatch(l -> !l.id().equals(link.id()) && l.tokenHash().equals(link.tokenHash()));
        if (duplicateHash) {
            throw new DuplicateTokenHashException();
        }
        byId.put(link.id(), link);
    }

    @Override
    public Optional<DownloadLink> findByTokenHash(TokenHash hash) {
        return byId.values().stream().filter(l -> l.tokenHash().equals(hash)).findFirst();
    }

    @Override
    public Optional<DownloadLink> findById(LinkId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<DownloadLink> findByStatement(StatementId statementId) {
        return byId.values().stream()
                .filter(l -> l.statementId().equals(statementId))
                .sorted(Comparator.comparing(DownloadLink::issuedAt).reversed())
                .toList();
    }

    /** Same predicate as the domain pre-check, applied atomically; mirrors the SQL conditional UPDATE. */
    @Override
    public synchronized boolean tryConsume(LinkId id, Instant now) {
        if (failNextConsume) {
            failNextConsume = false;
            return false;
        }
        DownloadLink link = byId.get(id);
        if (link == null || link.redeemability(now) != Redeemability.OK) {
            return false;
        }
        byId.put(id, link.withDownloadCount(link.downloadCount() + 1));
        return true;
    }

    @Override
    public synchronized boolean revoke(LinkId id, Instant now) {
        DownloadLink link = byId.get(id);
        if (link == null || link.revokedAt() != null) {
            return false;
        }
        byId.put(id, link.revoke(now));
        return true;
    }

    public int size() {
        return byId.size();
    }
}
