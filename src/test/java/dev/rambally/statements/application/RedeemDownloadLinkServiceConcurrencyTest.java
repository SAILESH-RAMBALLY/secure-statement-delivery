package dev.rambally.statements.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import dev.rambally.statements.application.crypto.AesGcmEnvelopeCipher;
import dev.rambally.statements.application.fakes.FakeKeyProvider;
import dev.rambally.statements.application.fakes.FixedTokenGenerator;
import dev.rambally.statements.application.fakes.InMemoryDownloadLinkRepository;
import dev.rambally.statements.application.fakes.InMemoryStatementRepository;
import dev.rambally.statements.application.fakes.InMemoryStatementStorage;
import dev.rambally.statements.application.fakes.RecordingAuditLog;
import dev.rambally.statements.application.port.in.RequestContext;
import dev.rambally.statements.application.port.in.UploadStatementCommand;
import dev.rambally.statements.domain.AccountNumber;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.DownloadLink;
import dev.rambally.statements.domain.Fixtures;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.LinkPolicy;
import dev.rambally.statements.domain.LinkToken;
import dev.rambally.statements.domain.RedemptionOutcome;
import dev.rambally.statements.domain.Statement;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.StatementPeriod;
import dev.rambally.statements.domain.exception.LinkNotRedeemableException;
import dev.rambally.statements.support.TestPdfs;

import org.junit.jupiter.api.Test;

/** The orchestration itself holds no shared mutable state; the only arbiter is the repository's atomic consume. */
class RedeemDownloadLinkServiceConcurrencyTest {

    private static final int THREADS = 64;

    @Test
    void sixty_four_threads_against_the_in_memory_fake_yield_exactly_one_success() throws Exception {
        InMemoryStatementRepository statements = new InMemoryStatementRepository();
        InMemoryDownloadLinkRepository links = new InMemoryDownloadLinkRepository();
        InMemoryStatementStorage storage = new InMemoryStatementStorage();
        FakeKeyProvider keys = new FakeKeyProvider();
        AesGcmEnvelopeCipher cipher = new AesGcmEnvelopeCipher();
        RecordingAuditLog audit = new RecordingAuditLog();
        Clock clock = Clock.fixed(Fixtures.NOW, ZoneOffset.UTC);

        StatementId id = new UploadStatementService(statements, storage, keys, cipher, new StatementSizePolicy(1 << 20), clock)
                .upload(new UploadStatementCommand(new CustomerId("C-1001"), new AccountNumber("1234567890"),
                        StatementPeriod.parse("2026-09"), TestPdfs.minimal(), new Principal(new CustomerId("admin"), true)));
        Statement statement = statements.findById(id).orElseThrow();
        LinkToken token = FixedTokenGenerator.tokenNumber(8);
        DownloadLink link = DownloadLink.issue(LinkId.newId(), statement, token.hash(), new LinkPolicy(Duration.ofHours(1), 1), Fixtures.NOW);
        links.save(link);

        RedeemDownloadLinkService service = new RedeemDownloadLinkService(links, statements, storage, keys, cipher, audit, clock);
        CountDownLatch gate = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        List<RedemptionOutcome> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                gate.await();
                try {
                    service.redeem(token.value(), new RequestContext("ip", "ua"));
                    successes.incrementAndGet();
                } catch (LinkNotRedeemableException e) {
                    failures.add(e.outcome());
                }
                return null;
            }));
        }
        gate.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures).hasSize(THREADS - 1)
                .allMatch(o -> o == RedemptionOutcome.EXHAUSTED || o == RedemptionOutcome.LOST_RACE);
        assertThat(links.findById(link.id()).orElseThrow().downloadCount()).isEqualTo(1);
        assertThat(audit.redemptionOutcomes()).hasSize(THREADS).containsOnlyOnce(RedemptionOutcome.SUCCESS);
    }
}
