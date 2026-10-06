package dev.rambally.statements.bootstrap;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Mints RS256 tokens with the dev/test issuer so end-to-end tests can authenticate. */
public final class TestTokens {

    private TestTokens() {
    }

    public static String mint(JwtEncoder encoder, AppProperties properties, String subject, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.security().devIssuer())
                .audience(List.of(properties.security().audience()))
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(10)))
                .claim(properties.security().rolesClaim(), roles)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
