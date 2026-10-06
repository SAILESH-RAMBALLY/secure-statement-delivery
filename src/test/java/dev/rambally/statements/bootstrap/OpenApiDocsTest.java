package dev.rambally.statements.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiDocsTest {

    @LocalServerPort
    int port;

    @Test
    void api_docs_are_public_and_declare_the_bearer_scheme_and_public_server_url() {
        ResponseEntity<String> response = RestClient.create("http://localhost:" + port)
                .get().uri("/v3/api-docs").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"bearer\"")
                .contains("\"scheme\":\"bearer\"")
                .contains("\"url\":\"https://statements.example.test\"")
                .contains("/api/admin/statements");
    }

    @Test
    void swagger_ui_is_served_without_authentication() {
        ResponseEntity<String> response = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, resp) -> { }).build()
                .get().uri("/swagger-ui/index.html").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
