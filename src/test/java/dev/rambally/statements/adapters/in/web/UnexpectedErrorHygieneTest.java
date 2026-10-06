package dev.rambally.statements.adapters.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;

import dev.rambally.statements.application.port.in.UploadStatementUseCase;
import dev.rambally.statements.support.TestPdfs;
import dev.rambally.statements.support.WebSliceTest;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/** A server-side fault is a 500 with a fixed body: never a 400 blamed on the client, never a stack trace. */
@WebSliceTest(controllers = AdminStatementController.class)
@Import(UnexpectedErrorHygieneTest.Stubs.class)
class UnexpectedErrorHygieneTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Stubs {
        @Bean
        UploadStatementUseCase uploadStatementUseCase() {
            return command -> {
                throw new UncheckedIOException(new IOException("disk on fire at /data/statements/secret-path"));
            };
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void infrastructure_failure_is_a_sanitised_500_problem_detail() throws Exception {
        mvc.perform(multipart("/api/admin/statements")
                        .file(new MockMultipartFile("file", "s.pdf", "application/pdf", TestPdfs.minimal()))
                        .param("customerId", "C-1001").param("accountNumber", "1234567890").param("period", "2026-09")
                        .with(jwt().jwt(j -> j.subject("ops-admin")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Request failed"))
                .andExpect(jsonPath("$.instance").value("/api"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("secret-path"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Exception"))));
    }
}
