package dev.rambally.statements.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Bad configuration fails at startup with a validation error, not at the first request. */
class AppPropertiesValidationTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "app.public-base-url=https://statements.example.test",
                    "app.security.roles-claim=roles",
                    "app.security.audience=secure-statements",
                    "app.security.dev-issuer=http://localhost:8080/dev",
                    "app.storage.root=/tmp/statements",
                    "app.crypto.kek=",
                    "app.crypto.kek-id=local-kek-v1",
                    "app.crypto.dev-fallback-allowed=true",
                    "app.statement.max-size-bytes=10485760",
                    "app.link.ttl=PT24H",
                    "app.link.max-downloads=1",
                    "app.download.max-concurrent=16");

    @Test
    void valid_configuration_binds() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            AppProperties props = context.getBean(AppProperties.class);
            assertThat(props.link().maxDownloads()).isEqualTo(1);
            assertThat(props.demo().customers()).isEmpty();
        });
    }

    @Test
    void rejects_policy_values_out_of_bounds() {
        runner.withPropertyValues("app.link.max-downloads=11").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.statement.max-size-bytes=10").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.download.max-concurrent=0").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejects_missing_required_values() {
        runner.withPropertyValues("app.security.audience=").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.crypto.kek-id=").run(context -> assertThat(context).hasFailed());
    }
}
