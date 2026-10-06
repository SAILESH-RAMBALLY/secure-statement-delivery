package dev.rambally.statements.bootstrap;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

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
        @Valid @NotNull Link link) {

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

    public record StatementLimits(@Min(1024) @Max(52428800) long maxSizeBytes) {
    }

    /** Bounds are enforced again by the domain LinkPolicy; these are the configured defaults. */
    public record Link(@NotNull Duration ttl, @Min(1) @Max(10) int maxDownloads) {
    }
}
