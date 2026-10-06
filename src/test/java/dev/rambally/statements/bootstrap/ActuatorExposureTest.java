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

/** Health is public (for the container probe); metrics are for operators with the ADMIN role only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ActuatorExposureTest {

    @LocalServerPort
    int port;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    AppProperties properties;

    private ResponseEntity<String> get(String path, String token) {
        RestClient.RequestHeadersSpec<?> spec = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build()
                .get().uri(path);
        if (token != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return spec.retrieve().toEntity(String.class);
    }

    @Test
    void health_and_probes_are_public_and_readiness_reports_the_database() {
        assertThat(get("/actuator/health", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/actuator/health/liveness", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> readiness = get("/actuator/health/readiness", null);
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void metrics_and_prometheus_require_the_admin_role() {
        String customer = TestTokens.mint(jwtEncoder, properties, "C-1001", List.of("CUSTOMER"));
        String admin = TestTokens.mint(jwtEncoder, properties, "ops-admin", List.of("ADMIN"));

        for (String path : List.of("/actuator/metrics", "/actuator/prometheus")) {
            assertThat(get(path, null).getStatusCode()).as(path + " anonymous").isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(get(path, customer).getStatusCode()).as(path + " customer").isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(get(path, admin).getStatusCode().value()).as(path + " admin").isNotIn(401, 403);
        }
    }

    @Test
    void other_actuator_endpoints_are_not_exposed_at_all() {
        String admin = TestTokens.mint(jwtEncoder, properties, "ops-admin", List.of("ADMIN"));

        assertThat(get("/actuator/env", admin).getStatusCode().value()).isIn(403, 404);
        assertThat(get("/actuator/beans", admin).getStatusCode().value()).isIn(403, 404);
    }
}
