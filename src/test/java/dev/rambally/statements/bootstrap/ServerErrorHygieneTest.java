package dev.rambally.statements.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/** Over real HTTP, no error path for an unmapped URL echoes the URL, a message or a stack trace. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ServerErrorHygieneTest {

    private static final String TOKEN_LIKE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP-";

    @LocalServerPort
    int port;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    AppProperties properties;

    private RestClient client() {
        return RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
    }

    @Test
    void unmapped_paths_with_a_token_like_segment_never_echo_it_authenticated_or_not() {
        String customer = TestTokens.mint(jwtEncoder, properties, "C-1001", List.of("CUSTOMER"));

        ResponseEntity<String> anonymous = client().get().uri("/nothing/" + TOKEN_LIKE).retrieve().toEntity(String.class);
        ResponseEntity<String> authenticated = client().get().uri("/nothing/" + TOKEN_LIKE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer).retrieve().toEntity(String.class);
        ResponseEntity<String> apiTypo = client().get().uri("/api/statementz/" + TOKEN_LIKE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer).retrieve().toEntity(String.class);
        ResponseEntity<String> downloadExtra = client().get().uri("/download/" + TOKEN_LIKE + "/extra").retrieve().toEntity(String.class);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(authenticated.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(apiTypo.getStatusCode()).as("unknown API paths are closed, not open").isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(downloadExtra.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        for (ResponseEntity<String> response : List.of(anonymous, authenticated, apiTypo, downloadExtra)) {
            assertThat(response.getBody()).doesNotContain(TOKEN_LIKE).doesNotContain("Exception").doesNotContain("\tat ");
        }
        assertThat(downloadExtra.getBody()).contains("\"instance\":\"/download\"");
    }

    @Test
    void an_unparseable_multipart_body_is_a_client_error_never_a_500() {
        String admin = TestTokens.mint(jwtEncoder, properties, "ops-admin", List.of("ADMIN"));
        RestClient client = client();

        ResponseEntity<String> download = client.post().uri("/download/abc")
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data").body("garbage").retrieve().toEntity(String.class);
        ResponseEntity<String> upload = client.post().uri("/api/admin/statements")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data").body("garbage").retrieve().toEntity(String.class);

        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(download.getBody()).isEqualTo(dev.rambally.statements.adapters.in.web.DownloadProblem.NOT_FOUND_BODY);
        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void firewall_rejected_and_trace_requests_never_echo_the_path() {
        RestClient client = client();

        ResponseEntity<String> semicolonDownload = client.get().uri("/download/" + TOKEN_LIKE + ";x").retrieve().toEntity(String.class);
        ResponseEntity<String> semicolonApi = client.get().uri("/api/statements;jsessionid=" + TOKEN_LIKE).retrieve().toEntity(String.class);
        ResponseEntity<String> trace = client.method(org.springframework.http.HttpMethod.TRACE).uri("/download/" + TOKEN_LIKE)
                .retrieve().toEntity(String.class);

        assertThat(semicolonDownload.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(semicolonDownload.getBody()).isEqualTo(dev.rambally.statements.adapters.in.web.DownloadProblem.NOT_FOUND_BODY);
        assertThat(semicolonApi.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        for (ResponseEntity<String> response : List.of(semicolonDownload, semicolonApi, trace)) {
            assertThat(response.getBody()).doesNotContain(TOKEN_LIKE).doesNotContain("\"path\"");
        }
    }
}
