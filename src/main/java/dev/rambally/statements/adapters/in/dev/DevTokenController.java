package dev.rambally.statements.adapters.in.dev;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Development-only token issuer so the API can be exercised from Swagger UI without an identity
 * provider. Exists only under the {@code dev} profile; the prod guard refuses to start if a JwtEncoder exists.
 */
@RestController
@Profile("dev")
@RequestMapping("/dev")
@Tag(name = "Development", description = "Only present in the dev profile")
public class DevTokenController {

    private static final Set<String> ROLES = Set.of("CUSTOMER", "ADMIN");

    public record TokenRequest(@NotBlank String subject, @NotEmpty List<String> roles, @Min(1) @Max(120) Integer ttlMinutes) {
    }

    public record TokenResponse(String token, Instant expiresAt) {
    }

    private final JwtEncoder encoder;
    private final DevTokenSettings settings;

    public DevTokenController(JwtEncoder encoder, DevTokenSettings settings) {
        this.encoder = encoder;
        this.settings = settings;
    }

    @Operation(summary = "Mint a development JWT",
            description = "subject becomes the customer id; roles are CUSTOMER and/or ADMIN; ttlMinutes defaults to 60 (max 120).")
    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        if (!ROLES.containsAll(request.roles())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roles must be a subset of " + ROLES);
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(request.ttlMinutes() == null ? 60 : request.ttlMinutes()));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .audience(List.of(settings.audience()))
                .subject(request.subject())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(settings.rolesClaim(), request.roles())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, expiresAt);
    }
}
