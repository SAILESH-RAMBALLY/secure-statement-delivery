package dev.rambally.statements.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import dev.rambally.statements.adapters.in.web.DownloadProblem;
import dev.rambally.statements.adapters.out.persistence.PostgresContainerConfig;
import dev.rambally.statements.domain.Sha256;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/** The race at HTTP level on PostgreSQL: one 200 with the right bytes, every other response the constant 404. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.download.max-concurrent=64")
@ActiveProfiles({"dev", "test"})
@Import(PostgresContainerConfig.class)
class ParallelDownloadIT {

    static final int PARALLEL = 10;

    @LocalServerPort
    int port;

    @Test
    void ten_parallel_downloads_yield_exactly_one_200_and_nine_identical_404s() throws Exception {
        EndToEndFlow flow = new EndToEndFlow(port);
        byte[] pdf = EndToEndFlow.samplePdf();
        String admin = flow.mintToken("ops-admin", "ADMIN");
        String customer = flow.mintToken("C-3003", "CUSTOMER");
        String statementId = flow.upload(admin, "C-3003", "3333333333", "2026-06", pdf);
        EndToEndFlow.Issued issued = flow.issueLink(customer, statementId);

        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL);
        List<Future<ResponseEntity<byte[]>>> futures = new ArrayList<>();
        for (int i = 0; i < PARALLEL; i++) {
            futures.add(pool.submit(() -> {
                gate.await();
                return flow.download(issued.token());
            }));
        }
        gate.countDown();
        List<ResponseEntity<byte[]>> responses = new ArrayList<>();
        for (Future<ResponseEntity<byte[]>> f : futures) {
            responses.add(f.get());
        }
        pool.shutdown();

        List<ResponseEntity<byte[]>> ok = responses.stream().filter(r -> r.getStatusCode().value() == 200).toList();
        List<ResponseEntity<byte[]>> notFound = responses.stream().filter(r -> r.getStatusCode().value() == 404).toList();
        assertThat(ok).hasSize(1);
        assertThat(Sha256.of(ok.getFirst().getBody())).isEqualTo(Sha256.of(pdf));
        assertThat(notFound).hasSize(PARALLEL - 1)
                .allSatisfy(r -> assertThat(new String(r.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                        .isEqualTo(DownloadProblem.NOT_FOUND_BODY));
    }
}
