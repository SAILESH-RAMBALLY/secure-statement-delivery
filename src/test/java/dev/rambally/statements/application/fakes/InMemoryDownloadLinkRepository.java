package dev.rambally.statements.application.fakes;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.rambally.statements.application.port.out.DownloadLinkRepository;
import dev.rambally.statements.domain.DownloadLink;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.TokenHash;
import dev.rambally.statements.domain.exception.DuplicateTokenHashException;

public final class InMemoryDownloadLinkRepository implements DownloadLinkRepository {

    private final Map<LinkId, DownloadLink> byId = new ConcurrentHashMap<>();

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

    public int size() {
        return byId.size();
    }
}
