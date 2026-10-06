package dev.rambally.statements.bootstrap;

import java.util.ArrayList;
import java.util.List;

import dev.rambally.statements.adapters.out.crypto.LocalKekKeyProvider;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Fail-fast guard for the {@code prod} profile. Runs as an EnvironmentPostProcessor, i.e. before any bean
 * is created, before Flyway touches the production database and before a connection pool is opened, so
 * a misconfigured deployment dies with one readable message instead of serving customers with a
 * development key or an open Swagger UI.
 */
public final class ProdGuard implements EnvironmentPostProcessor, Ordered {

    private static final String JWT = "spring.security.oauth2.resourceserver.jwt.";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> problems = check(environment);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start in prod:\n - " + String.join("\n - ", problems));
        }
    }

    static List<String> check(Environment env) {
        List<String> problems = new ArrayList<>();

        String kek = env.getProperty("app.crypto.kek", "");
        if (kek.isBlank()) {
            problems.add("APP_CRYPTO_KEK is not set (base64 of 32 random bytes; generate with: openssl rand -base64 32)");
        } else if (kek.trim().equals(LocalKekKeyProvider.DEV_KEK_BASE64)) {
            problems.add("APP_CRYPTO_KEK is the built-in development key; statements would not be protected");
        }
        if (env.getProperty("app.crypto.dev-fallback-allowed", Boolean.class, false)) {
            problems.add("app.crypto.dev-fallback-allowed must be false");
        }

        String baseUrl = env.getProperty("app.public-base-url", "");
        if (!baseUrl.toLowerCase().startsWith("https://")) {
            problems.add("APP_PUBLIC_BASE_URL must be an https URL (download links are credentials)");
        }

        String datasource = env.getProperty("spring.datasource.url", "");
        if (datasource.toLowerCase().startsWith("jdbc:h2:")) {
            problems.add("the datasource is H2; production needs PostgreSQL");
        }

        for (String profile : List.of("dev", "demo", "test")) {
            if (env.acceptsProfiles(Profiles.of(profile))) {
                problems.add("profile '" + profile + "' must not be active together with prod");
            }
        }

        boolean decoderConfigured = !env.getProperty(JWT + "issuer-uri", "").isBlank()
                || !env.getProperty(JWT + "jwk-set-uri", "").isBlank()
                || !env.getProperty(JWT + "public-key-location", "").isBlank();
        if (!decoderConfigured) {
            problems.add("no JWT decoder source: set APP_JWT_ISSUER_URI (issuer-uri), or jwk-set-uri / public-key-location");
        }

        if (env.getProperty("springdoc.api-docs.enabled", Boolean.class, true)
                || env.getProperty("springdoc.swagger-ui.enabled", Boolean.class, true)) {
            problems.add("springdoc api-docs and swagger-ui must be disabled in prod");
        }
        return problems;
    }

    /** After ConfigData, so profile-specific YAML and environment variables are already merged. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
