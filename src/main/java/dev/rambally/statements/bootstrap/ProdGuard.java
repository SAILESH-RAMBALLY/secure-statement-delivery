package dev.rambally.statements.bootstrap;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
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
        } else {
            checkKekBytes(kek.trim(), problems);
        }
        if (env.getProperty("app.crypto.dev-fallback-allowed", Boolean.class, false)) {
            problems.add("app.crypto.dev-fallback-allowed must be false");
        }

        String baseUrl = env.getProperty("app.public-base-url", "");
        if (!baseUrl.toLowerCase().startsWith("https://")) {
            problems.add("APP_PUBLIC_BASE_URL must be an https URL (download links are credentials)");
        }

        String datasource = env.getProperty("spring.datasource.url", "");
        if (!datasource.toLowerCase().startsWith("jdbc:postgresql:")) {
            if (datasource.isBlank()) {
                problems.add("no datasource: activate the postgres profile and set SPRING_DATASOURCE_URL"
                        + " (otherwise an in-memory database would be used)");
            } else if (datasource.toLowerCase().startsWith("jdbc:h2:")) {
                problems.add("the datasource is H2; production needs PostgreSQL");
            } else {
                problems.add("the datasource is not PostgreSQL; production needs PostgreSQL");
            }
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

        if ("native".equalsIgnoreCase(env.getProperty("server.forward-headers-strategy", ""))
                && env.getProperty("server.tomcat.remoteip.internal-proxies", "").isBlank()
                && env.getProperty("server.tomcat.remoteip.trusted-proxies", "").isBlank()) {
            problems.add("forwarded headers are on but no proxy is trusted: set SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES"
                    + " to the load balancer's address so other callers can't fake their IP");
        }

        if (env.getProperty(JWT + "audiences", "").isBlank()) {
            problems.add("no JWT audience: set APP_JWT_AUDIENCE so tokens minted for other services are rejected");
        }

        if (env.getProperty("springdoc.api-docs.enabled", Boolean.class, true)
                || env.getProperty("springdoc.swagger-ui.enabled", Boolean.class, true)) {
            problems.add("springdoc api-docs and swagger-ui must be disabled in prod");
        }
        return problems;
    }

    /** Compares decoded bytes, so no alternative base64 spelling of the development key can slip through. */
    private static void checkKekBytes(String base64, List<String> problems) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            problems.add("APP_CRYPTO_KEK is not valid base64");
            return;
        }
        if (bytes.length != 32) {
            problems.add("APP_CRYPTO_KEK must decode to exactly 32 bytes (found " + bytes.length + ")");
        }
        if (MessageDigest.isEqual(bytes, Base64.getDecoder().decode(LocalKekKeyProvider.DEV_KEK_BASE64))) {
            problems.add("APP_CRYPTO_KEK is the built-in development key; statements would not be protected");
        }
    }

    /** After ConfigData, so profile-specific YAML and environment variables are already merged. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
