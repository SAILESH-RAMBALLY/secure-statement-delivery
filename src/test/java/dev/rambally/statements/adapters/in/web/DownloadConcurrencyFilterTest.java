package dev.rambally.statements.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The permit must be held for the whole exchange, including writing the body, so a slow client cannot
 * hold decrypted plaintext outside the bulkhead.
 */
class DownloadConcurrencyFilterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private static MockHttpServletRequest download() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/download/abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP-");
        request.setRequestURI("/download/abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP-");
        return request;
    }

    @Test
    void request_beyond_the_permit_count_gets_503_with_retry_after_while_another_is_in_flight() throws Exception {
        DownloadConcurrencyFilter filter = new DownloadConcurrencyFilter(1, registry);
        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FilterChain slowChain = (req, res) -> {
            inFlight.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            res.getOutputStream().write(1);
        };
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> first = pool.submit(() -> {
            filter.doFilter(download(), new MockHttpServletResponse(), slowChain);
            return null;
        });
        assertThat(inFlight.await(5, TimeUnit.SECONDS)).isTrue();

        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(download(), rejected, new MockFilterChain());

        assertThat(rejected.getStatus()).isEqualTo(503);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("2");
        assertThat(rejected.getContentAsString()).isEqualTo(DownloadProblem.SERVICE_UNAVAILABLE_BODY);
        assertThat(registry.counter(DownloadConcurrencyFilter.REJECTED_COUNTER).count()).isEqualTo(1.0);

        release.countDown();
        first.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        MockHttpServletResponse accepted = new MockHttpServletResponse();
        filter.doFilter(download(), accepted, new MockFilterChain());
        assertThat(accepted.getStatus()).isEqualTo(200);
    }

    @Test
    void permit_is_released_even_when_the_chain_throws() throws Exception {
        DownloadConcurrencyFilter filter = new DownloadConcurrencyFilter(1, registry);
        FilterChain exploding = (req, res) -> {
            throw new ServletException("boom");
        };

        try {
            filter.doFilter(download(), new MockHttpServletResponse(), exploding);
        } catch (ServletException expected) {
            // propagated to the container as usual
        }

        MockHttpServletResponse next = new MockHttpServletResponse();
        filter.doFilter(download(), next, new MockFilterChain());
        assertThat(next.getStatus()).isEqualTo(200);
    }

    @Test
    void paths_outside_download_are_never_limited() throws IOException, ServletException {
        DownloadConcurrencyFilter filter = new DownloadConcurrencyFilter(1, registry);
        MockHttpServletRequest api = new MockHttpServletRequest("GET", "/api/statements");
        api.setRequestURI("/api/statements");

        assertThat(filter.shouldNotFilter(api)).isTrue();
        assertThat(filter.shouldNotFilter(download())).isFalse();
    }
}
