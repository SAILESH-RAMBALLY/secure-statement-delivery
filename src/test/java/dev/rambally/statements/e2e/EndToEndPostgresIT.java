package dev.rambally.statements.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import dev.rambally.statements.adapters.out.persistence.PostgresContainerConfig;
import dev.rambally.statements.domain.Sha256;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** The same journey against real PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
@Import(PostgresContainerConfig.class)
class EndToEndPostgresIT {

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @Test
    void same_flow_on_postgres() {
        EndToEndFlow flow = new EndToEndFlow(port);
        byte[] pdf = EndToEndFlow.samplePdf();
        String admin = flow.mintToken("ops-admin", "ADMIN");
        String customer = flow.mintToken("C-1001", "CUSTOMER");

        String statementId = flow.upload(admin, "C-1001", "1234567890", "2026-07", pdf);
        EndToEndFlow.Issued issued = flow.issueLink(customer, statementId);

        ResponseEntity<byte[]> first = flow.download(issued.token());
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Sha256.of(first.getBody())).isEqualTo(Sha256.of(pdf));
        EndToEndFlow.assertConstantNotFound(flow.download(issued.token()));

        List<String> outcomes = jdbc.sql("SELECT COALESCE(outcome, event_type) FROM download_audit WHERE link_id = :id ORDER BY id")
                .param("id", java.util.UUID.fromString(issued.linkId())).query(String.class).list();
        assertThat(outcomes).containsExactly("LINK_ISSUED", "SUCCESS", "EXHAUSTED");
    }
}
