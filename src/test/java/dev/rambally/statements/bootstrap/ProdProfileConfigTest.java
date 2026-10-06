package dev.rambally.statements.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/** The committed prod profile must switch off every development convenience; the guard then verifies it at runtime. */
class ProdProfileConfigTest {

    private final Properties prod = load();

    private static Properties load() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application-prod.yml"));
        return factory.getObject();
    }

    @Test
    void swagger_and_api_docs_are_disabled() {
        assertThat(prod.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(prod.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    @Test
    void dev_key_fallback_is_off_and_the_kek_comes_from_the_environment() {
        assertThat(prod.getProperty("app.crypto.dev-fallback-allowed")).isEqualTo("false");
        assertThat(prod.getProperty("app.crypto.kek")).isEqualTo("${APP_CRYPTO_KEK}");
    }

    @Test
    void jwt_decoding_comes_from_a_real_issuer_and_logs_are_structured() {
        assertThat(prod.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")).isEqualTo("${APP_JWT_ISSUER_URI}");
        assertThat(prod.getProperty("logging.structured.format.console")).isEqualTo("ecs");
        assertThat(prod.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,prometheus");
    }
}
