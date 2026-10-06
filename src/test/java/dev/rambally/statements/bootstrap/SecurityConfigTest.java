package dev.rambally.statements.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * The default security chain has no securityMatcher and ends in anyRequest().denyAll(), so any
 * path that is not explicitly opened is closed: anonymous callers get 401, authenticated callers 403.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityConfigTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private AppProperties properties;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @Test
    void unknown_path_is_denied_without_token_401() {
        ResponseEntity<String> response = client().get().uri("/nothing/here").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        assertThat(response.getHeaders().getContentType()).isNotNull().satisfies(t -> assertThat(t.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
        assertThat(response.getBody()).contains("\"status\":401").doesNotContain("/nothing/here");
    }

    @Test
    void unknown_path_is_denied_with_valid_token_403() {
        String token = TestTokens.mint(jwtEncoder, properties, "C-1001", List.of("CUSTOMER"));

        ResponseEntity<String> response = client().get().uri("/nothing/here")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType()).isNotNull().satisfies(t -> assertThat(t.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
        assertThat(response.getBody()).contains("\"status\":403").doesNotContain("/nothing/here");
    }

    @Test
    void error_dispatch_is_not_denied() {
        // A container error forward to /error must not be turned into a misleading 401/403 by the
        // fail-closed chain. An unauthenticated request to an unmapped public path must stay a 404.
        ResponseEntity<String> response = client().get().uri("/actuator/health/does-not-exist")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void health_is_reachable_without_authentication() {
        ResponseEntity<String> response = client().get().uri("/actuator/health").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
