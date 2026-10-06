package dev.rambally.statements.adapters.out.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import dev.rambally.statements.application.port.out.DownloadLinkRepository;
import dev.rambally.statements.domain.LinkId;

/** Fires N racers at tryConsume from a latch so they hit the database at the same instant. */
final class ConcurrentConsume {

    private ConcurrentConsume() {
    }

    static int race(DownloadLinkRepository links, LinkId id, Instant now, int threads) throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    gate.await();
                    if (links.tryConsume(id, now)) {
                        successes.incrementAndGet();
                    }
                    return null;
                }));
            }
            gate.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        return successes.get();
    }
}
