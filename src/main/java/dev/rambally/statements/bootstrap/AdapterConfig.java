package dev.rambally.statements.bootstrap;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import dev.rambally.statements.adapters.out.crypto.LocalKekKeyProvider;
import dev.rambally.statements.adapters.out.notification.LoggingNotificationAdapter;
import dev.rambally.statements.adapters.out.storage.FilesystemStatementStorage;
import dev.rambally.statements.adapters.out.storage.StorageHealthIndicator;
import dev.rambally.statements.adapters.out.token.SecureRandomTokenGenerator;
import dev.rambally.statements.application.port.out.KeyProvider;
import dev.rambally.statements.application.port.out.NotificationPort;
import dev.rambally.statements.application.port.out.StatementStorage;
import dev.rambally.statements.application.port.out.TokenGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Driven adapters that need configuration values. Component-scanned adapters (JDBC repositories) wire themselves. */
@Configuration
public class AdapterConfig {

    private static final Logger log = LoggerFactory.getLogger(AdapterConfig.class);

    /** Microsecond ticks: TIMESTAMP columns hold microseconds, so in-memory and stored instants always agree. */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.of(1, ChronoUnit.MICROS));
    }

    @Bean
    KeyProvider keyProvider(AppProperties properties) {
        LocalKekKeyProvider provider = LocalKekKeyProvider.fromConfig(
                properties.crypto().kek(), properties.crypto().kekId(), properties.crypto().devFallbackAllowed());
        if (provider.usingDevFallback()) {
            log.warn("""

                    ************************************************************************
                    *  USING THE BUILT-IN DEVELOPMENT KEY-ENCRYPTION KEY.                  *
                    *  Statements are NOT protected. Set APP_CRYPTO_KEK (openssl rand -base64 32). *
                    ************************************************************************
                    """);
        }
        return provider;
    }

    @Bean
    StatementStorage statementStorage(AppProperties properties) {
        return new FilesystemStatementStorage(properties.storage().root());
    }

    @Bean
    StorageHealthIndicator storageHealthIndicator(AppProperties properties) {
        return new StorageHealthIndicator(properties.storage().root());
    }

    @Bean
    TokenGenerator tokenGenerator() {
        return new SecureRandomTokenGenerator();
    }

    @Bean
    NotificationPort notificationPort() {
        return new LoggingNotificationAdapter();
    }

}
