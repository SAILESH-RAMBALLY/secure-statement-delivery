package dev.rambally.statements.bootstrap;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * All application configuration, bound once here and handed to the hexagon as plain values.
 * The application layer never sees this type.
 */
@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
        @NotNull URI publicBaseUrl,
        @Valid @NotNull Security security,
        @Valid @NotNull Storage storage,
        @Valid @NotNull Crypto crypto,
        @Valid @NotNull StatementLimits statement,
        @Valid @NotNull Link link,
        @Valid @NotNull Download download,
        @Valid Demo demo) {

    /** The demo section is optional; absent means "seed nothing". */
    public AppProperties {
        demo = demo == null ? new Demo(List.of()) : demo;
    }

    public record Security(
            @NotBlank String rolesClaim,
            @NotBlank String audience,
            @NotBlank String devIssuer) {
    }

    public record Storage(@NotNull Path root) {
    }

    /** {@code kek} may be blank; whether that is acceptable is decided by {@code devFallbackAllowed}. */
    public record Crypto(String kek, @NotBlank String kekId, boolean devFallbackAllowed) {
    }

    /** Upper bound is the schema CHECK and the multipart limit (10 MiB); the three must agree. */
    public record StatementLimits(@Min(1024) @Max(10485760) long maxSizeBytes) {
    }

    /** Bounds are enforced again by the domain LinkPolicy; these are the configured defaults. */
    public record Link(@NotNull Duration ttl, @Min(1) @Max(10) int maxDownloads) {
    }

    /** Bulkhead size: each in-flight download may hold ~2x the statement size limit in memory. */
    public record Download(@Min(1) @Max(256) int maxConcurrent) {
    }

    /** Demo profile seed data; empty outside the demo profile. */
    public record Demo(@NotNull List<@Valid DemoCustomer> customers) {
        public Demo {
            customers = customers == null ? List.of() : List.copyOf(customers);
        }
    }

    public record DemoCustomer(@NotBlank String customerId, @NotBlank String accountNumber, @Min(1) @Max(12) int months) {
    }
}
