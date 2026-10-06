package dev.rambally.statements.adapters.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.rambally.statements.application.port.in.UploadStatementUseCase;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.support.WebSliceTest;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spring's default ProblemDetail echoes the request path in {@code instance} and often in {@code detail}
 * ("No static resource /download/<token>"). On this service a path may carry a download token, so
 * every error body is normalised: instance is a fixed prefix and detail never contains client input.
 */
@WebSliceTest(controllers = AdminStatementController.class)
@Import(ApiExceptionHandlerTest.Stubs.class)
class ApiExceptionHandlerTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Stubs {
        @Bean
        UploadStatementUseCase uploadStatementUseCase() {
            return command -> StatementId.newId();
        }
    }

    private static final String TOKEN_LIKE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP-";

    @Autowired
    MockMvc mvc;

    @Test
    void unmapped_api_path_with_token_like_segment_never_echoes_it() throws Exception {
        mvc.perform(get("/api/statements/" + TOKEN_LIKE + "/x")
                        .with(jwt().jwt(j -> j.subject("C-1001")).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.instance").value("/api"))
                .andExpect(content().string(Matchers.not(Matchers.containsString(TOKEN_LIKE))));
    }

    @Test
    void unmapped_download_path_with_token_like_segment_never_echoes_it() throws Exception {
        mvc.perform(post("/download/" + TOKEN_LIKE))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.instance").value("/download"))
                .andExpect(content().string(Matchers.not(Matchers.containsString(TOKEN_LIKE))));
    }

    @Test
    void wrong_method_on_known_path_has_sanitised_detail() throws Exception {
        mvc.perform(get("/api/admin/statements")
                        .with(jwt().jwt(j -> j.subject("ops-admin")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.detail").value("Method Not Allowed"))
                .andExpect(jsonPath("$.instance").value("/api"));
    }
}
