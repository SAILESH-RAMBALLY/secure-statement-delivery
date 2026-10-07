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

    @Test
    void every_operation_has_a_summary_and_documented_responses_and_the_download_404_shows_no_token() {
        String body = RestClient.create("http://localhost:" + port).get().uri("/v3/api-docs").retrieve().body(String.class);
        tools.jackson.databind.JsonNode root = tools.jackson.databind.json.JsonMapper.builder().build().readTree(body);

        var paths = root.get("paths");
        assertThat(paths.size()).isGreaterThanOrEqualTo(5);
        paths.properties().forEach(path -> path.getValue().properties().forEach(op -> {
            assertThat(op.getValue().has("summary")).as(op.getKey() + " " + path.getKey() + " summary").isTrue();
            assertThat(op.getValue().get("responses").size()).as(op.getKey() + " " + path.getKey() + " responses").isGreaterThan(0);
        }));
        var download = paths.get("/download/{token}").get("get").get("responses");
        assertThat(download.has("404")).isTrue();
        assertThat(download.get("404").toString()).doesNotMatch(".*[A-Za-z0-9_-]{43}.*");
    }

    @Test
    void the_caller_identity_is_never_offered_as_request_input() {
        String body = RestClient.create("http://localhost:" + port).get().uri("/v3/api-docs").retrieve().body(String.class);
        tools.jackson.databind.JsonNode paths = tools.jackson.databind.json.JsonMapper.builder().build().readTree(body).get("paths");

        // The admin upload's customerId names whose statement it is (a multipart field), not the caller.
        paths.properties().forEach(path -> path.getValue().properties().forEach(op -> {
            var params = op.getValue().get("parameters");
            if (params != null) {
                boolean upload = path.getKey().equals("/api/admin/statements");
                params.forEach(param -> assertThat(param.get("name").asString())
                        .as(op.getKey() + " " + path.getKey())
                        .isNotIn(upload ? new String[] {"admin", "actor"} : new String[] {"customerId", "admin", "actor"}));
            }
        }));
        assertThat(paths.get("/api/statements").get("get").has("parameters")).isFalse();
        assertThat(paths.get("/api/statements/{statementId}/links").get("post").get("parameters").size()).isEqualTo(1);
    }
}
