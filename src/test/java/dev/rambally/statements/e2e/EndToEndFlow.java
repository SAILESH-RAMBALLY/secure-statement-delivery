package dev.rambally.statements.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Map;

import dev.rambally.statements.adapters.in.web.DownloadProblem;
import dev.rambally.statements.support.TestPdfs;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** The reviewer's journey over real HTTP, shared by the H2 and PostgreSQL end-to-end tests. */
public final class EndToEndFlow {

    public record Issued(String linkId, URI url, String token) {
    }

    private final RestClient client;

    public EndToEndFlow(int port) {
        this.client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @SuppressWarnings("unchecked")
    public String mintToken(String subject, String... roles) {
        ResponseEntity<Map> response = client.post().uri("/dev/token")
                .body(Map.of("subject", subject, "roles", List.of(roles), "ttlMinutes", 30))
                .retrieve().toEntity(Map.class);
        assertThat(response.getStatusCode()).as("mint token for " + subject).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("token");
    }

    public String upload(String adminToken, String customerId, String account, String period, byte[] pdf) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return "client-chosen-name.pdf";
            }
        });
        form.add("customerId", customerId);
        form.add("accountNumber", account);
        form.add("period", period);
        ResponseEntity<Map> response = client.post().uri("/api/admin/statements")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve().toEntity(Map.class);
        assertThat(response.getStatusCode()).as("upload").isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("statementId");
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listStatements(String token) {
        ResponseEntity<List> response = client.get().uri("/api/statements")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(List.class);
        assertThat(response.getStatusCode()).as("list").isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    public Issued issueLink(String token, String statementId) {
        ResponseEntity<Map> response = client.post().uri("/api/statements/" + statementId + "/links")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(Map.class);
        assertThat(response.getStatusCode()).as("issue link").isEqualTo(HttpStatus.CREATED);
        URI url = URI.create((String) response.getBody().get("url"));
        String path = url.getPath();
        return new Issued((String) response.getBody().get("linkId"), url, path.substring(path.lastIndexOf('/') + 1));
    }

    /** The issued URL points at the configured public base; the test hits the same path on the local port. */
    public ResponseEntity<byte[]> download(String token) {
        return client.get().uri("/download/" + token).retrieve().toEntity(byte[].class);
    }

    public ResponseEntity<byte[]> head(String token) {
        return client.head().uri("/download/" + token).retrieve().toEntity(byte[].class);
    }

    public ResponseEntity<String> raw(java.util.function.Function<RestClient, RestClient.RequestHeadersSpec<?>> call) {
        return call.apply(client).retrieve().toEntity(String.class);
    }

    public static void assertConstantNotFound(ResponseEntity<byte[]> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(DownloadProblem.NOT_FOUND_BODY);
    }

    public static byte[] samplePdf() {
        return TestPdfs.ofSize(4096);
    }
}
