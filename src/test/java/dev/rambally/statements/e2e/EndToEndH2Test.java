package dev.rambally.statements.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import dev.rambally.statements.bootstrap.AppProperties;
import dev.rambally.statements.domain.Sha256;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * The whole journey over HTTP on H2, no Docker required: upload as admin, list as customer, issue a link,
 * download once, be refused the second time, and check the audit trail. Also proves the token never
 * reaches a log line or a metric tag, and that nothing on disk is plaintext.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
@ExtendWith(OutputCaptureExtension.class)
class EndToEndH2Test {

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MeterRegistry meters;

    @Autowired
    AppProperties properties;

    @Test
    void admin_uploads_customer_lists_issues_link_downloads_once_then_404_and_audit_records_it(CapturedOutput output) throws IOException {
        EndToEndFlow flow = new EndToEndFlow(port);
        byte[] pdf = EndToEndFlow.samplePdf();
        String admin = flow.mintToken("ops-admin", "ADMIN");
        String customer = flow.mintToken("C-1001", "CUSTOMER");

        String statementId = flow.upload(admin, "C-1001", "1234567890", "2026-09", pdf);

        assertThat(flow.listStatements(customer)).anySatisfy(s -> {
            assertThat(s.get("statementId")).isEqualTo(statementId);
            assertThat(s.get("accountNumber")).isEqualTo("******7890");
        });
        assertThat(flow.listStatements(flow.mintToken("C-2002", "CUSTOMER"))).noneMatch(s -> s.get("statementId").equals(statementId));

        EndToEndFlow.Issued issued = flow.issueLink(customer, statementId);
        assertThat(issued.url().toString()).startsWith("https://statements.example.test/download/");

        // HEAD never redeems.
        assertThat(flow.head(issued.token()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<byte[]> first = flow.download(issued.token());
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(first.getHeaders().getFirst("Content-Disposition")).isEqualTo("attachment; filename=\"statement-7890-2026-09.pdf\"");
        assertThat(Sha256.of(first.getBody())).isEqualTo(Sha256.of(pdf));

        EndToEndFlow.assertConstantNotFound(flow.download(issued.token()));

        List<String> outcomes = jdbc.sql("SELECT COALESCE(outcome, event_type) FROM download_audit WHERE link_id = :id ORDER BY id")
                .param("id", java.util.UUID.fromString(issued.linkId())).query(String.class).list();
        assertThat(outcomes).containsExactly("LINK_ISSUED", "SUCCESS", "EXHAUSTED");

        // Hygiene: the token is in exactly one place, the issue response. Positive controls first, so a
        // silenced logger or disabled metrics cannot make these assertions pass vacuously.
        assertThat(output.getAll()).contains("/download/[redacted]").doesNotContain(issued.token());
        assertThat(meters.getMeters()).flatExtracting(m -> m.getId().getTags()).extracting(Tag::getValue)
                .noneMatch(v -> v.contains(issued.token()));
        var downloadUris = meters.getMeters().stream().map(Meter::getId)
                .filter(id -> id.getName().equals("http.server.requests"))
                .map(id -> id.getTag("uri")).filter(uri -> uri != null && uri.startsWith("/download")).toList();
        assertThat(downloadUris).isNotEmpty().allMatch("/download/{token}"::equals);

        // Nothing on disk is plaintext.
        try (Stream<Path> files = Files.walk(properties.storage().root())) {
            List<Path> stored = files.filter(Files::isRegularFile).toList();
            assertThat(stored).isNotEmpty();
            for (Path file : stored) {
                assertThat(new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1)).doesNotContain("%PDF-");
            }
        }
    }

    @Test
    void unknown_and_malformed_tokens_get_the_same_constant_404_and_are_audited() {
        EndToEndFlow flow = new EndToEndFlow(port);

        EndToEndFlow.assertConstantNotFound(flow.download("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP-"));
        EndToEndFlow.assertConstantNotFound(flow.download("nope"));

        List<String> outcomes = jdbc.sql("SELECT outcome FROM download_audit WHERE event_type = 'REDEMPTION' AND link_id IS NULL ORDER BY id")
                .query(String.class).list();
        assertThat(outcomes).contains("UNKNOWN_TOKEN", "MALFORMED_TOKEN");
    }
}
