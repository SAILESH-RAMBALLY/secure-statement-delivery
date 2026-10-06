package dev.rambally.statements.adapters.in.web;

import java.io.IOException;
import java.util.concurrent.Semaphore;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bulkhead for the download endpoint. Each in-flight download holds up to ~2x the statement size limit in
 * memory (ciphertext plus plaintext), so the number in flight is capped. The permit is held for the whole
 * exchange, including writing the body, so a slow client cannot keep plaintext outside the bulkhead.
 * Saturation is a capacity signal (503 + Retry-After) that reveals nothing about any link.
 */
public final class DownloadConcurrencyFilter extends OncePerRequestFilter {

    public static final String REJECTED_COUNTER = "download.rejected.saturated";
    private static final String PATH_PREFIX = "/download/";

    private final Semaphore permits;
    private final Counter rejected;

    public DownloadConcurrencyFilter(int maxConcurrent, MeterRegistry registry) {
        this.permits = new Semaphore(maxConcurrent, true);
        this.rejected = registry.counter(REJECTED_COUNTER);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI() == null || !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!permits.tryAcquire()) {
            rejected.increment();
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader(HttpHeaders.RETRY_AFTER, "2");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(DownloadProblem.SERVICE_UNAVAILABLE_BODY);
            return;
        }
        try {
            chain.doFilter(request, response);
            response.flushBuffer();
        } finally {
            permits.release();
        }
    }
}
