package dev.rambally.statements.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.rambally.statements.application.port.in.UploadStatementCommand;
import dev.rambally.statements.application.port.in.UploadStatementUseCase;
import dev.rambally.statements.domain.AccountNumber;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.StatementPeriod;
import dev.rambally.statements.domain.exception.DuplicateStatementException;
import dev.rambally.statements.domain.exception.InvalidStatementException;
import dev.rambally.statements.support.TestPdfs;
import dev.rambally.statements.support.WebSliceTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@WebSliceTest(controllers = AdminStatementController.class)
@Import(AdminStatementControllerTest.Stubs.class)
class AdminStatementControllerTest {

    static final StatementId CREATED = StatementId.of("550e8400-e29b-41d4-a716-446655440000");

    /** A recording stub, not a mock: it implements the port and lets the test decide the outcome. */
    static final class StubUploadUseCase implements UploadStatementUseCase {
        UploadStatementCommand lastCommand;
        RuntimeException failWith;

        @Override
        public StatementId upload(UploadStatementCommand command) {
            lastCommand = command;
            if (failWith != null) {
                throw failWith;
            }
            return CREATED;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Stubs {
        @Bean
        StubUploadUseCase uploadStatementUseCase() {
            return new StubUploadUseCase();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    StubUploadUseCase useCase;

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("ops-admin").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor customer() {
        return jwt().jwt(j -> j.subject("C-1001").claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static MockMultipartHttpServletRequestBuilder upload(byte[] pdf) {
        return multipart("/api/admin/statements")
                .file(new MockMultipartFile("file", "whatever-the-client-called-it.pdf", "application/pdf", pdf))
                .param("customerId", "C-1001")
                .param("accountNumber", "1234567890")
                .param("period", "2026-09");
    }

    @Test
    void admin_jwt_uploads_pdf_and_gets_201_with_location() throws Exception {
        useCase.failWith = null;

        mvc.perform(upload(TestPdfs.minimal()).with(admin()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/statements/" + CREATED))
                .andExpect(jsonPath("$.statementId").value(CREATED.toString()));

        assertThat(useCase.lastCommand.customerId()).isEqualTo(new CustomerId("C-1001"));
        assertThat(useCase.lastCommand.accountNumber()).isEqualTo(new AccountNumber("1234567890"));
        assertThat(useCase.lastCommand.period()).isEqualTo(StatementPeriod.parse("2026-09"));
        assertThat(useCase.lastCommand.actor().admin()).isTrue();
        assertThat(useCase.lastCommand.actor().customerId()).isEqualTo(new CustomerId("ops-admin"));
        assertThat(useCase.lastCommand.pdfBytes()).isEqualTo(TestPdfs.minimal());
    }

    @Test
    void customer_jwt_gets_403_problem_detail() throws Exception {
        mvc.perform(upload(TestPdfs.minimal()).with(customer()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.instance").value("/api"));
    }

    @Test
    void no_jwt_gets_401_problem_detail_with_bearer_challenge() throws Exception {
        mvc.perform(upload(TestPdfs.minimal()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void non_pdf_gets_415_and_duplicate_gets_409() throws Exception {
        useCase.failWith = new InvalidStatementException(InvalidStatementException.Reason.NOT_PDF);
        mvc.perform(upload("<html>".getBytes(StandardCharsets.US_ASCII)).with(admin()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("Statement rejected: NOT_PDF"));

        useCase.failWith = new DuplicateStatementException(new CustomerId("C-1001"), new AccountNumber("1234567890"),
                StatementPeriod.parse("2026-09"));
        mvc.perform(upload(TestPdfs.minimal()).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void malformed_period_and_account_number_are_400_without_echoing_input() throws Exception {
        useCase.failWith = null;

        mvc.perform(multipart("/api/admin/statements")
                        .file(new MockMultipartFile("file", "s.pdf", "application/pdf", TestPdfs.minimal()))
                        .param("customerId", "C-1001")
                        .param("accountNumber", "<script>alert(1)</script>")
                        .param("period", "2026-09")
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))));

        mvc.perform(multipart("/api/admin/statements")
                        .file(new MockMultipartFile("file", "s.pdf", "application/pdf", TestPdfs.minimal()))
                        .param("customerId", "C-1001")
                        .param("accountNumber", "1234567890")
                        .param("period", "September")
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Statement rejected: BAD_PERIOD"));
    }

    @Test
    void missing_file_part_is_400() throws Exception {
        mvc.perform(multipart("/api/admin/statements")
                        .param("customerId", "C-1001").param("accountNumber", "1234567890").param("period", "2026-09")
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.instance").value("/api"));
    }
}
