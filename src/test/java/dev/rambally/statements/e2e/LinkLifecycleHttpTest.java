package dev.rambally.statements.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** Revoke and list links over real HTTP: owner, other customer and admin. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
class LinkLifecycleHttpTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @Test
    void owner_revokes_then_download_is_404_and_audit_shows_LINK_REVOKED_and_REVOKED() {
        EndToEndFlow flow = new EndToEndFlow(port);
        String admin = flow.mintToken("ops-admin", "ADMIN");
        String owner = flow.mintToken("C-4004", "CUSTOMER");
        String other = flow.mintToken("C-5005", "CUSTOMER");
        String statementId = flow.upload(admin, "C-4004", "4444444444", "2026-05", EndToEndFlow.samplePdf());
        EndToEndFlow.Issued issued = flow.issueLink(owner, statementId);

        // Another customer cannot revoke it, and learns nothing beyond "not found".
        ResponseEntity<String> otherAttempt = flow.raw(c -> c.delete().uri("/api/links/" + issued.linkId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + other));
        assertThat(otherAttempt.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> revoke = flow.raw(c -> c.delete().uri("/api/links/" + issued.linkId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner));
        assertThat(revoke.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> again = flow.raw(c -> c.delete().uri("/api/links/" + issued.linkId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner));
        assertThat(again.getStatusCode()).as("idempotent").isEqualTo(HttpStatus.NO_CONTENT);

        EndToEndFlow.assertConstantNotFound(flow.download(issued.token()));

        List<String> outcomes = jdbc.sql("SELECT COALESCE(outcome, event_type) FROM download_audit WHERE link_id = :id ORDER BY id")
                .param("id", java.util.UUID.fromString(issued.linkId())).query(String.class).list();
        assertThat(outcomes).containsExactly("LINK_ISSUED", "LINK_REVOKED", "REVOKED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listing_links_shows_status_and_counts_but_never_token_or_hash_and_admin_can_revoke() {
        EndToEndFlow flow = new EndToEndFlow(port);
        String admin = flow.mintToken("ops-admin", "ADMIN");
        String owner = flow.mintToken("C-6006", "CUSTOMER");
        String statementId = flow.upload(admin, "C-6006", "6666666666", "2026-04", EndToEndFlow.samplePdf());
        EndToEndFlow.Issued first = flow.issueLink(owner, statementId);
        EndToEndFlow.Issued second = flow.issueLink(owner, statementId);
        flow.download(first.token());

        ResponseEntity<String> adminRevoke = flow.raw(c -> c.delete().uri("/api/links/" + second.linkId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin));
        assertThat(adminRevoke.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> listing = flow.raw(c -> c.get().uri("/api/statements/" + statementId + "/links")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner));
        assertThat(listing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listing.getBody())
                .contains("\"status\":\"EXHAUSTED\"").contains("\"status\":\"REVOKED\"")
                .contains("\"downloadCount\":1")
                .doesNotContain(first.token()).doesNotContain(second.token())
                .doesNotContain("tokenHash").doesNotContain("hash");

        ResponseEntity<String> otherListing = flow.raw(c -> c.get().uri("/api/statements/" + statementId + "/links")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + flow.mintToken("C-7007", "CUSTOMER")));
        assertThat(otherListing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
