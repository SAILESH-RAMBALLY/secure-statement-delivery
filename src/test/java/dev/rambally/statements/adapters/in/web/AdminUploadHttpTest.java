package dev.rambally.statements.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import dev.rambally.statements.application.port.out.StatementRepository;
import dev.rambally.statements.bootstrap.AppProperties;
import dev.rambally.statements.bootstrap.TestTokens;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.support.TestPdfs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Full stack over real HTTP: real JWT decoding and role mapping, real multipart limits enforced by the
 * container (MockMvc never enforces them), real use case against H2 and the filesystem.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminUploadHttpTest {

    @LocalServerPort
    int port;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    AppProperties properties;

    @Autowired
    StatementRepository statements;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    private static MultiValueMap<String, Object> form(byte[] pdf, String account, String period) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return "statement.pdf";
            }
        });
        form.add("customerId", "C-1001");
        form.add("accountNumber", account);
        form.add("period", period);
        return form;
    }

    private ResponseEntity<String> upload(String token, byte[] pdf, String account, String period) {
        RestClient.RequestBodySpec spec = client().post().uri("/api/admin/statements")
                .contentType(MediaType.MULTIPART_FORM_DATA);
        if (token != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return spec.body(form(pdf, account, period)).retrieve().toEntity(String.class);
    }

    @Test
    void admin_token_uploads_and_the_statement_is_persisted() {
        String admin = TestTokens.mint(jwtEncoder, properties, "ops-admin", List.of("ADMIN"));

        ResponseEntity<String> response = upload(admin, TestPdfs.minimal(), "5550001111", "2026-08");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"statementId\"");
        assertThat(statements.findByCustomer(new CustomerId("C-1001")))
                .anySatisfy(s -> assertThat(s.accountNumber().value()).isEqualTo("5550001111"));
    }

    @Test
    void customer_token_is_forbidden_and_missing_token_is_unauthorized() {
        String customer = TestTokens.mint(jwtEncoder, properties, "C-1001", List.of("CUSTOMER"));

        assertThat(upload(customer, TestPdfs.minimal(), "5550002222", "2026-08").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(upload(null, TestPdfs.minimal(), "5550002222", "2026-08").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void oversize_multipart_gets_413() {
        String admin = TestTokens.mint(jwtEncoder, properties, "ops-admin", List.of("ADMIN"));

        ResponseEntity<String> response = upload(admin, TestPdfs.ofSize(10 * 1024 * 1024 + 1), "5550003333", "2026-08");

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).contains("\"instance\":\"/api\"");
    }
}
