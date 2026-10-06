package dev.rambally.statements.adapters.in.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/** Without the dev profile the controller bean does not exist and /dev/** is not on the public chain. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DevTokenAbsentOutsideDevTest {

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext context;

    @Test
    void endpoint_is_absent_and_the_path_is_closed() {
        assertThat(context.getBeanNamesForType(DevTokenController.class)).isEmpty();

        ResponseEntity<String> response = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, resp) -> { }).build()
                .post().uri("/dev/token")
                .body(Map.of("subject", "C-1001", "roles", List.of("CUSTOMER")))
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
