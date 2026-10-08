package dev.rambally.statements.adapters.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.rambally.statements.application.port.in.IssueDownloadLinkUseCase;
import dev.rambally.statements.application.port.in.ListLinksUseCase;
import dev.rambally.statements.application.port.in.ListStatementsUseCase;
import dev.rambally.statements.support.WebSliceTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/** A database that can't be reached is a 503 the client can retry, not a 500. */
@WebSliceTest(controllers = StatementController.class)
@Import(DatabaseUnavailableTest.Stubs.class)
class DatabaseUnavailableTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Stubs {
        @Bean
        ListStatementsUseCase listStatementsUseCase() {
            return actor -> {
                throw new CannotGetJdbcConnectionException("connection refused");
            };
        }

        @Bean
        IssueDownloadLinkUseCase issueDownloadLinkUseCase() {
            return command -> {
                throw new IllegalStateException("not used");
            };
        }

        @Bean
        ListLinksUseCase listLinksUseCase() {
            return (statementId, actor) -> {
                throw new IllegalStateException("not used");
            };
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void an_unreachable_database_is_a_503_with_retry_after() throws Exception {
        mvc.perform(get("/api/statements")
                        .with(jwt().jwt(j -> j.subject("C-1001")).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.detail").value("Try again shortly"))
                .andExpect(jsonPath("$.instance").value("/api"));
    }
}
