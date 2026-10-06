package dev.rambally.statements.bootstrap;

import java.net.URI;

import jakarta.validation.Valid;
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
        @Valid @NotNull Security security) {

    public record Security(
            @NotBlank String rolesClaim,
            @NotBlank String audience,
            @NotBlank String devIssuer) {
    }
}
