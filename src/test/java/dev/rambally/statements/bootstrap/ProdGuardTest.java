package dev.rambally.statements.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import dev.rambally.statements.SecureStatementDeliveryApplication;
import dev.rambally.statements.adapters.out.crypto.LocalKekKeyProvider;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.env.MockEnvironment;

/**
 * Production refuses to start with any development artefact active. Each refusal is a separate rule so
 * the startup error names exactly what is wrong.
 */
class ProdGuardTest {

    private static MockEnvironment validProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("app.crypto.kek", "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LTEyMzQ=");
        env.setProperty("app.crypto.dev-fallback-allowed", "false");
        env.setProperty("app.public-base-url", "https://statements.example.com");
        env.setProperty("spring.datasource.url", "jdbc:postgresql://db:5432/statements");
        env.setProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri", "https://idp.example.com/realms/bank");
        env.setProperty("spring.security.oauth2.resourceserver.jwt.audiences", "secure-statements");
        env.setProperty("springdoc.api-docs.enabled", "false");
        env.setProperty("springdoc.swagger-ui.enabled", "false");
        return env;
    }

    @Test
    void valid_prod_configuration_passes() {
        assertThat(ProdGuard.check(validProd())).isEmpty();
    }

    @Test
    void public_key_location_is_an_acceptable_alternative_to_issuer_uri() {
        MockEnvironment env = validProd();
        env.setProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri", "");
        env.setProperty("spring.security.oauth2.resourceserver.jwt.public-key-location", "file:/run/secrets/idp.pem");

        assertThat(ProdGuard.check(env)).isEmpty();
    }

    @Test
    void fails_when_dev_kek_is_configured_or_kek_is_blank() {
        MockEnvironment devKey = validProd();
        devKey.setProperty("app.crypto.kek", LocalKekKeyProvider.DEV_KEK_BASE64);
        assertThat(ProdGuard.check(devKey)).anySatisfy(p -> assertThat(p).contains("development key"));

        MockEnvironment blank = validProd();
        blank.setProperty("app.crypto.kek", "");
        assertThat(ProdGuard.check(blank)).anySatisfy(p -> assertThat(p).contains("APP_CRYPTO_KEK"));

        MockEnvironment fallback = validProd();
        fallback.setProperty("app.crypto.dev-fallback-allowed", "true");
        assertThat(ProdGuard.check(fallback)).anySatisfy(p -> assertThat(p).contains("dev-fallback-allowed"));
    }

    @Test
    void detects_the_dev_key_under_any_base64_spelling_and_rejects_undecodable_or_short_keys() {
        MockEnvironment unpadded = validProd();
        unpadded.setProperty("app.crypto.kek", LocalKekKeyProvider.DEV_KEK_BASE64.replace("=", ""));
        assertThat(ProdGuard.check(unpadded)).anySatisfy(p -> assertThat(p).contains("development key"));

        MockEnvironment garbage = validProd();
        garbage.setProperty("app.crypto.kek", "not base64!");
        assertThat(ProdGuard.check(garbage)).anySatisfy(p -> assertThat(p).contains("valid base64"));

        MockEnvironment shortKey = validProd();
        shortKey.setProperty("app.crypto.kek", "c2hvcnQ=");
        assertThat(ProdGuard.check(shortKey)).anySatisfy(p -> assertThat(p).contains("32 bytes"));
    }

    @Test
    void fails_when_no_audience_is_configured() {
        MockEnvironment env = validProd();
        env.setProperty("spring.security.oauth2.resourceserver.jwt.audiences", "");

        assertThat(ProdGuard.check(env)).anySatisfy(p -> assertThat(p).contains("audience"));
    }

    @Test
    void fails_when_public_base_url_is_not_https() {
        MockEnvironment env = validProd();
        env.setProperty("app.public-base-url", "http://statements.example.com");

        assertThat(ProdGuard.check(env)).anySatisfy(p -> assertThat(p).contains("https"));
    }

    @Test
    void fails_when_datasource_is_h2() {
        MockEnvironment env = validProd();
        env.setProperty("spring.datasource.url", "jdbc:h2:file:/data/db/statements");

        assertThat(ProdGuard.check(env)).anySatisfy(p -> assertThat(p).contains("H2"));
    }

    @Test
    void fails_when_dev_or_demo_profiles_are_also_active() {
        MockEnvironment env = validProd();
        env.setActiveProfiles("prod", "dev", "demo");

        List<String> problems = ProdGuard.check(env);
        assertThat(problems).anySatisfy(p -> assertThat(p).contains("dev")).anySatisfy(p -> assertThat(p).contains("demo"));
    }

    @Test
    void fails_when_no_jwt_decoder_source_is_configured() {
        MockEnvironment env = validProd();
        env.setProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri", "");

        assertThat(ProdGuard.check(env)).anySatisfy(p -> assertThat(p).contains("issuer-uri"));
    }

    @Test
    void fails_when_swagger_is_enabled() {
        MockEnvironment env = validProd();
        env.setProperty("springdoc.swagger-ui.enabled", "true");

        assertThat(ProdGuard.check(env)).anySatisfy(p -> assertThat(p).contains("swagger"));
    }

    @Test
    void does_nothing_outside_the_prod_profile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        env.setProperty("app.crypto.kek", LocalKekKeyProvider.DEV_KEK_BASE64);

        new ProdGuard().postProcessEnvironment(env, null);
    }

    @Test
    void the_application_refuses_to_start_in_prod_with_the_dev_key_before_touching_any_database() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(SecureStatementDeliveryApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("prod")
                .properties(
                        "APP_CRYPTO_KEK=" + LocalKekKeyProvider.DEV_KEK_BASE64,
                        "APP_PUBLIC_BASE_URL=https://statements.example.com",
                        "APP_JWT_ISSUER_URI=https://idp.example.com",
                        "SPRING_DATASOURCE_URL=jdbc:postgresql://nowhere:5432/none")
                .run())
                .hasMessageContaining("Refusing to start")
                .hasMessageContaining("development key");
    }

    @Test
    void a_missing_mandatory_variable_produces_the_readable_message_not_a_placeholder_error() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(SecureStatementDeliveryApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("prod")
                .properties(
                        "APP_PUBLIC_BASE_URL=https://statements.example.com",
                        "APP_JWT_ISSUER_URI=https://idp.example.com")
                .run())
                .hasMessageContaining("Refusing to start")
                .hasMessageContaining("APP_CRYPTO_KEK is not set");
    }
}
