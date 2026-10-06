package dev.rambally.statements.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import dev.rambally.statements.application.port.out.StatementRepository;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.e2e.EndToEndFlow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

/** Seeding goes through the real upload use case, so the demo data is encrypted and downloadable like any other. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "demo", "test"})
class DemoDataSeederTest {

    @LocalServerPort
    int port;

    @Autowired
    StatementRepository statements;

    @Autowired
    DemoDataSeeder seeder;

    @Autowired
    AppProperties properties;

    @Test
    void seeds_five_statements_once_and_a_second_run_does_not_duplicate() throws Exception {
        assertThat(statements.count()).isEqualTo(5);
        assertThat(statements.findByCustomer(new CustomerId("C-1001"))).hasSize(3);
        assertThat(statements.findByCustomer(new CustomerId("C-2002"))).hasSize(2);

        seeder.run(new DefaultApplicationArguments());

        assertThat(statements.count()).isEqualTo(5);
    }

    @Test
    void seeded_statements_are_encrypted_at_rest_and_downloadable_end_to_end() throws IOException {
        try (Stream<Path> files = Files.walk(properties.storage().root())) {
            assertThat(files.filter(Files::isRegularFile)).isNotEmpty().allSatisfy(file ->
                    assertThat(new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1)).doesNotContain("%PDF-"));
        }

        EndToEndFlow flow = new EndToEndFlow(port);
        String customer = flow.mintToken("C-1001", "CUSTOMER");
        String statementId = (String) flow.listStatements(customer).getFirst().get("statementId");
        EndToEndFlow.Issued issued = flow.issueLink(customer, statementId);

        var download = flow.download(issued.token());
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(download.getBody(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
