package dev.rambally.statements.bootstrap;

import java.time.Clock;

import dev.rambally.statements.application.StatementSizePolicy;
import dev.rambally.statements.application.UploadStatementService;
import dev.rambally.statements.application.crypto.AesGcmEnvelopeCipher;
import dev.rambally.statements.application.port.in.UploadStatementUseCase;
import dev.rambally.statements.application.port.out.KeyProvider;
import dev.rambally.statements.application.port.out.StatementRepository;
import dev.rambally.statements.application.port.out.StatementStorage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The only place that knows which adapter satisfies which port. The use cases themselves are framework-free. */
@Configuration
public class UseCaseConfig {

    @Bean
    AesGcmEnvelopeCipher envelopeCipher() {
        return new AesGcmEnvelopeCipher();
    }

    @Bean
    StatementSizePolicy statementSizePolicy(AppProperties properties) {
        return new StatementSizePolicy(properties.statement().maxSizeBytes());
    }

    @Bean
    UploadStatementUseCase uploadStatementUseCase(StatementRepository statements, StatementStorage storage,
            KeyProvider keyProvider, AesGcmEnvelopeCipher cipher, StatementSizePolicy sizePolicy, Clock clock) {
        return new UploadStatementService(statements, storage, keyProvider, cipher, sizePolicy, clock);
    }
}
