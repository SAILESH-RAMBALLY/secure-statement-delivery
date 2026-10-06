package dev.rambally.statements.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import dev.rambally.statements.application.Principal;
import dev.rambally.statements.domain.CustomerId;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtPrincipalResolverTest {

    private final JwtPrincipalResolver resolver = new JwtPrincipalResolver();

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("ignored").header("alg", "RS256").subject(subject).claim("roles", List.of("x")).build();
    }

    @Test
    void builds_principal_from_subject_and_role_authorities_never_from_request_params() {
        JwtAuthenticationToken admin = new JwtAuthenticationToken(jwt("ops-admin"),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        JwtAuthenticationToken customer = new JwtAuthenticationToken(jwt("C-1001"),
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        assertThat(resolver.toPrincipal(admin)).isEqualTo(new Principal(new CustomerId("ops-admin"), true));
        assertThat(resolver.toPrincipal(customer)).isEqualTo(new Principal(new CustomerId("C-1001"), false));
    }

    @Test
    void rejects_missing_or_non_jwt_authentication_and_unusable_subjects() {
        assertThatThrownBy(() -> resolver.toPrincipal(null)).isInstanceOf(InvalidPrincipalException.class);
        assertThatThrownBy(() -> resolver.toPrincipal(new UsernamePasswordAuthenticationToken("u", "p")))
                .isInstanceOf(InvalidPrincipalException.class);
        assertThatThrownBy(() -> resolver.toPrincipal(new JwtAuthenticationToken(
                Jwt.withTokenValue("x").header("alg", "RS256").claim("foo", "bar").build(), List.of())))
                .isInstanceOf(InvalidPrincipalException.class);
    }

    @Test
    void only_supports_the_application_principal_type() throws NoSuchMethodException {
        var method = Sample.class.getDeclaredMethod("handler", Principal.class, String.class);
        var principalParam = new org.springframework.core.MethodParameter(method, 0);
        var stringParam = new org.springframework.core.MethodParameter(method, 1);

        assertThat(resolver.supportsParameter(principalParam)).isTrue();
        assertThat(resolver.supportsParameter(stringParam)).isFalse();
    }

    @SuppressWarnings("unused")
    static class Sample {
        void handler(Principal principal, String other) {
        }
    }
}
