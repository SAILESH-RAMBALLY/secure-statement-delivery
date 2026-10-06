package dev.rambally.statements.adapters.in.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
class DevTokenControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    JwtDecoder decoder;

    private RestClient client() {
        return RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void mints_rs256_token_that_the_resource_server_decoder_accepts_with_issuer_audience_and_roles() {
        ResponseEntity<Map> response = client().post().uri("/dev/token")
                .body(Map.of("subject", "C-1001", "roles", List.of("CUSTOMER"), "ttlMinutes", 30))
                .retrieve().toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) response.getBody().get("token");
        assertThat(response.getBody()).containsKey("expiresAt");

        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("C-1001");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(jwt.getAudience()).contains("secure-statements");
        assertThat(jwt.getIssuer().toString()).isEqualTo("http://localhost:8080/dev");
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
    }

    @Test
    void rejects_unknown_roles_and_excessive_ttl() {
        ResponseEntity<String> badRole = client().post().uri("/dev/token")
                .body(Map.of("subject", "C-1001", "roles", List.of("ROOT"))).retrieve().toEntity(String.class);
        ResponseEntity<String> longTtl = client().post().uri("/dev/token")
                .body(Map.of("subject", "C-1001", "roles", List.of("CUSTOMER"), "ttlMinutes", 1000)).retrieve().toEntity(String.class);

        assertThat(badRole.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(longTtl.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
