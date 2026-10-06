package dev.rambally.statements.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import dev.rambally.statements.application.crypto.AesGcmEnvelopeCipher;
import dev.rambally.statements.application.port.in.RedeemDownloadLinkUseCase;
import dev.rambally.statements.application.port.in.RequestContext;
import dev.rambally.statements.application.port.in.StatementDownload;
import dev.rambally.statements.application.port.out.AuditLog;
import dev.rambally.statements.application.port.out.DownloadLinkRepository;
import dev.rambally.statements.application.port.out.KeyProvider;
import dev.rambally.statements.application.port.out.StatementRepository;
import dev.rambally.statements.application.port.out.StatementStorage;
import dev.rambally.statements.domain.AuditEvent;
import dev.rambally.statements.domain.DownloadLink;
import dev.rambally.statements.domain.LinkToken;
import dev.rambally.statements.domain.RedemptionOutcome;
import dev.rambally.statements.domain.Sha256;
import dev.rambally.statements.domain.Statement;
import dev.rambally.statements.domain.exception.IntegrityException;
import dev.rambally.statements.domain.exception.LinkNotRedeemableException;

/**
 * The redemption pipeline: parse, look up by hash, domain pre-check, read ciphertext, decrypt (tag
 * verified, plaintext digest re-checked), then the single atomic conditional UPDATE, then audit.
 * Decrypting before consuming means a fault on the bank's side (missing file, bad key) never burns the
 * customer's one download; the cost under a genuine race is a few wasted decrypts, bounded by the size limit.
 * An infrastructure fault anywhere is audited as INTERNAL_ERROR so the trail never has a silent gap.
 */
public final class RedeemDownloadLinkService implements RedeemDownloadLinkUseCase {

    private static final int GCM_TAG_BYTES = 16;

    private final DownloadLinkRepository links;
    private final StatementRepository statements;
    private final StatementStorage storage;
    private final KeyProvider keyProvider;
    private final AesGcmEnvelopeCipher cipher;
    private final AuditLog audit;
    private final Clock clock;

    public RedeemDownloadLinkService(DownloadLinkRepository links, StatementRepository statements,
            StatementStorage storage, KeyProvider keyProvider, AesGcmEnvelopeCipher cipher, AuditLog audit, Clock clock) {
        this.links = links;
        this.statements = statements;
        this.storage = storage;
        this.keyProvider = keyProvider;
        this.cipher = cipher;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public StatementDownload redeem(String rawToken, RequestContext ctx) {
        Instant now = clock.instant();
        LinkToken token = LinkToken.parse(rawToken)
                .orElseThrow(() -> fail(now, RedemptionOutcome.MALFORMED_TOKEN, null, null, ctx));
        String prefix = token.hash().prefix();

        DownloadLink link = null;
        try {
            link = links.findByTokenHash(token.hash())
                    .orElseThrow(() -> fail(now, RedemptionOutcome.UNKNOWN_TOKEN, prefix, null, ctx));
            return redeem(link, now, prefix, ctx);
        } catch (LinkNotRedeemableException expected) {
            throw expected;
        } catch (RuntimeException infrastructure) {
            throw fail(now, RedemptionOutcome.INTERNAL_ERROR, prefix, link, ctx);
        }
    }

    private StatementDownload redeem(DownloadLink link, Instant now, String prefix, RequestContext ctx) {
        switch (link.redeemability(now)) {
            case EXPIRED -> throw fail(now, RedemptionOutcome.EXPIRED, prefix, link, ctx);
            case REVOKED -> throw fail(now, RedemptionOutcome.REVOKED, prefix, link, ctx);
            case EXHAUSTED -> throw fail(now, RedemptionOutcome.EXHAUSTED, prefix, link, ctx);
            case OK -> { }
        }

        Statement statement = statements.findById(link.statementId())
                .orElseThrow(() -> fail(now, RedemptionOutcome.STORAGE_MISSING, prefix, link, ctx));
        Optional<byte[]> ciphertext = storage.read(statement.storageKey(), statement.sizeBytes() + GCM_TAG_BYTES);
        if (ciphertext.isEmpty()) {
            throw fail(now, RedemptionOutcome.STORAGE_MISSING, prefix, link, ctx);
        }

        byte[] plaintext;
        try {
            plaintext = cipher.open(ciphertext.get(), statement.id(), statement.envelope(), keyProvider);
        } catch (IntegrityException e) {
            throw fail(now, RedemptionOutcome.INTEGRITY_FAILED, prefix, link, ctx);
        }
        if (!Sha256.of(plaintext).equals(statement.contentHash())) {
            throw fail(now, RedemptionOutcome.INTEGRITY_FAILED, prefix, link, ctx);
        }

        if (!links.tryConsume(link.id(), now)) {
            throw fail(now, RedemptionOutcome.LOST_RACE, prefix, link, ctx);
        }
        SideEffects.quietly(() -> audit.record(
                AuditEvent.redemption(now, RedemptionOutcome.SUCCESS, prefix, link, ctx.clientIp(), ctx.userAgent())));
        return new StatementDownload(statement.downloadFileName(), plaintext);
    }

    private LinkNotRedeemableException fail(Instant now, RedemptionOutcome outcome, String prefix, DownloadLink link,
            RequestContext ctx) {
        SideEffects.quietly(() -> audit.record(AuditEvent.redemption(now, outcome, prefix, link, ctx.clientIp(), ctx.userAgent())));
        return new LinkNotRedeemableException(outcome);
    }
}
