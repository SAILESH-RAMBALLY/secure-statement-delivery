package dev.rambally.statements.bootstrap;

import java.time.Clock;

import dev.rambally.statements.application.IssueDownloadLinkService;
import dev.rambally.statements.application.ListStatementsService;
import dev.rambally.statements.application.PublicBaseUrl;
import dev.rambally.statements.application.StatementSizePolicy;
import dev.rambally.statements.application.UploadStatementService;
import dev.rambally.statements.application.crypto.AesGcmEnvelopeCipher;
import dev.rambally.statements.application.port.in.IssueDownloadLinkUseCase;
import dev.rambally.statements.application.port.in.ListStatementsUseCase;
import dev.rambally.statements.application.port.in.UploadStatementUseCase;
import dev.rambally.statements.application.port.out.AuditLog;
import dev.rambally.statements.application.port.out.DownloadLinkRepository;
import dev.rambally.statements.application.port.out.KeyProvider;
import dev.rambally.statements.application.port.out.NotificationPort;
import dev.rambally.statements.application.port.out.StatementRepository;
import dev.rambally.statements.application.port.out.StatementStorage;
import dev.rambally.statements.application.port.out.TokenGenerator;
import dev.rambally.statements.domain.LinkPolicy;

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
    LinkPolicy linkPolicy(AppProperties properties) {
        return new LinkPolicy(properties.link().ttl(), properties.link().maxDownloads());
    }

    @Bean
    PublicBaseUrl publicBaseUrl(AppProperties properties) {
        return new PublicBaseUrl(properties.publicBaseUrl());
    }

    @Bean
    UploadStatementUseCase uploadStatementUseCase(StatementRepository statements, StatementStorage storage,
            KeyProvider keyProvider, AesGcmEnvelopeCipher cipher, StatementSizePolicy sizePolicy, Clock clock) {
        return new UploadStatementService(statements, storage, keyProvider, cipher, sizePolicy, clock);
    }

    @Bean
    IssueDownloadLinkUseCase issueDownloadLinkUseCase(StatementRepository statements, DownloadLinkRepository links,
            TokenGenerator tokens, AuditLog audit, NotificationPort notifications, PublicBaseUrl baseUrl,
            LinkPolicy policy, Clock clock) {
        return new IssueDownloadLinkService(statements, links, tokens, audit, notifications, baseUrl, policy, clock);
    }

    @Bean
    ListStatementsUseCase listStatementsUseCase(StatementRepository statements) {
        return new ListStatementsService(statements);
    }
}
