package dev.rambally.statements.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Two filter chains.
 *
 * <ol>
 *   <li>A public chain, selected by explicit matchers, for the capability-URL download endpoint,
 *       health probes, API docs and (dev only) the token issuer.</li>
 *   <li>A default chain with no matcher that applies to everything else: JWT resource server,
 *       role rules, and {@code anyRequest().denyAll()} so an unlisted path is closed, not open.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String PROBLEM_401 = "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
            + "\"detail\":\"Authentication required\",\"instance\":\"/api\"}";
    static final String PROBLEM_403 = "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
            + "\"detail\":\"Insufficient privileges\",\"instance\":\"/api\"}";

    private final Environment environment;
    private final AppProperties properties;

    SecurityConfig(Environment environment, AppProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @Bean
    @Order(1)
    SecurityFilterChain publicChain(HttpSecurity http) throws Exception {
        List<String> publicPaths = new ArrayList<>(List.of(
                "/download/**",
                "/actuator/health/**", "/actuator/health",
                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"));
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            publicPaths.add("/dev/**");
        }
        http.securityMatcher(publicPaths.toArray(String[]::new))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .cacheControl(Customizer.withDefaults())
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/metrics/**", "/actuator/prometheus").hasRole("ADMIN")
                        .requestMatchers("/api/statements/**", "/api/links/**").hasAnyRole("CUSTOMER", "ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(problemEntryPoint())
                        .accessDeniedHandler(problemAccessDeniedHandler()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemEntryPoint())
                        .accessDeniedHandler(problemAccessDeniedHandler()))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        String rolesClaim = properties.security().rolesClaim();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter((Jwt jwt) -> {
            List<String> roles = jwt.getClaimAsStringList(rolesClaim);
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            if (roles != null) {
                roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }
            return authorities;
        });
        return converter;
    }

    private AuthenticationEntryPoint problemEntryPoint() {
        return (request, response, exception) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED, PROBLEM_401);
        };
    }

    private AccessDeniedHandler problemAccessDeniedHandler() {
        return (request, response, exception) -> writeProblem(response, HttpServletResponse.SC_FORBIDDEN, PROBLEM_403);
    }

    private static void writeProblem(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }
}
