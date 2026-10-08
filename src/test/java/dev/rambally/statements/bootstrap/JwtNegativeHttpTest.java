package dev.rambally.statements.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * The negative half of authentication: tokens that are almost right must be rejected. Wrong key, wrong
 * audience, wrong issuer, expired, and valid-but-roleless all fail closed on the admin and operator paths.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JwtNegativeHttpTest {

    @LocalServerPort
    int port;

    @Autowired
    JwtEncoder trustedEncoder;

    @Autowired
    AppProperties properties;

    private ResponseEntity<String> call(String path, String token) {
        return RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build()
                .get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(String.class);
    }

    private String mint(JwtEncoder encoder, Consumer<JwtClaimsSet.Builder> customise) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.security().devIssuer())
                .audience(List.of(properties.security().audience()))
                .subject("ops-admin")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(10)))
                .claim(properties.security().rolesClaim(), List.of("ADMIN"));
        customise.accept(claims);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
                .getTokenValue();
    }

    private static JwtEncoder foreignEncoder() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(UUID.randomUUID().toString()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    private void assertUnauthorized(String token, String why) {
        for (String path : List.of("/api/statements", "/actuator/prometheus")) {
            ResponseEntity<String> response = call(path, token);
            assertThat(response.getStatusCode()).as(why + " on " + path).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).as(why).startsWith("Bearer");
            assertThat(response.getBody()).as(why).contains("\"status\":401").doesNotContain("error_description");
        }
    }

    @Test
    void a_token_signed_by_another_key_is_rejected() throws Exception {
        assertUnauthorized(mint(foreignEncoder(), c -> { }), "foreign key");
    }

    @Test
    void a_token_for_another_audience_is_rejected() {
        assertUnauthorized(mint(trustedEncoder, c -> c.audience(List.of("some-other-service"))), "wrong audience");
    }

    @Test
    void a_token_from_another_issuer_is_rejected() {
        assertUnauthorized(mint(trustedEncoder, c -> c.issuer("https://evil.example")), "wrong issuer");
    }

    @Test
    void an_expired_token_is_rejected() {
        assertUnauthorized(mint(trustedEncoder, c -> c.issuedAt(Instant.now().minus(Duration.ofMinutes(15)))
                .expiresAt(Instant.now().minus(Duration.ofMinutes(5)))), "expired");
    }

    @Test
    void a_valid_token_without_roles_is_authenticated_but_forbidden_on_role_gated_paths() {
        String roleless = mint(trustedEncoder, c -> c.claims(claims -> claims.remove(properties.security().rolesClaim())));

        assertThat(call("/api/statements", roleless).getStatusCode()).as("authenticated customer path").isEqualTo(HttpStatus.OK);
        assertThat(call("/actuator/prometheus", roleless).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> admin = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build()
                .post().uri("/api/admin/statements").header(HttpHeaders.AUTHORIZATION, "Bearer " + roleless)
                .retrieve().toEntity(String.class);
        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void a_malformed_bearer_value_is_rejected_with_the_same_401_body() {
        assertUnauthorized("not.a.jwt", "garbage");
    }

    @Test
    void a_trusted_token_whose_subject_is_not_a_usable_customer_id_is_rejected_with_401() {
        ResponseEntity<String> blank = call("/api/statements", mint(trustedEncoder, c -> c.subject("   ")));
        ResponseEntity<String> tooLong = call("/api/statements", mint(trustedEncoder, c -> c.subject("x".repeat(200))));

        for (ResponseEntity<String> response : List.of(blank, tooLong)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        }
    }
}
