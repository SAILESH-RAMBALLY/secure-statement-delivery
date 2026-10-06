package dev.rambally.statements.bootstrap;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;

import dev.rambally.statements.application.Principal;
import dev.rambally.statements.application.port.in.UploadStatementCommand;
import dev.rambally.statements.application.port.in.UploadStatementUseCase;
import dev.rambally.statements.application.port.out.StatementRepository;
import dev.rambally.statements.domain.AccountNumber;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.StatementPeriod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Demo profile only. Seeds a few statements for two customers through the real upload use case, so the
 * demo data is validated, encrypted and stored exactly like production data. Idempotent: skips when any
 * statement exists.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final Principal SEEDER = new Principal(new CustomerId("system-seed"), true);

    private final StatementRepository statements;
    private final UploadStatementUseCase upload;
    private final StatementPdfGenerator generator;
    private final AppProperties properties;
    private final Clock clock;

    public DemoDataSeeder(StatementRepository statements, UploadStatementUseCase upload, StatementPdfGenerator generator,
            AppProperties properties, Clock clock) {
        this.statements = statements;
        this.upload = upload;
        this.generator = generator;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (statements.count() > 0) {
            log.info("Demo seed skipped: {} statement(s) already present", statements.count());
            return;
        }
        YearMonth thisMonth = YearMonth.from(clock.instant().atOffset(ZoneOffset.UTC));
        int seeded = 0;
        for (AppProperties.DemoCustomer customer : properties.demo().customers()) {
            for (int monthsBack = 1; monthsBack <= customer.months(); monthsBack++) {
                CustomerId customerId = new CustomerId(customer.customerId());
                AccountNumber account = new AccountNumber(customer.accountNumber());
                StatementPeriod period = new StatementPeriod(thisMonth.minusMonths(monthsBack));
                byte[] pdf = generator.generate(customerId, account, period);
                upload.upload(new UploadStatementCommand(customerId, account, period, pdf, SEEDER));
                seeded++;
            }
        }
        log.info("""

                ============================================================================
                  Demo data seeded: {} statement(s).
                  Swagger UI : {}/swagger-ui.html
                  Mint tokens: POST /dev/token  {"subject":"ops-admin","roles":["ADMIN"]}
                                              {"subject":"C-1001","roles":["CUSTOMER"]}
                                              {"subject":"C-2002","roles":["CUSTOMER"]}
                ============================================================================
                """, seeded, properties.publicBaseUrl());
    }
}
