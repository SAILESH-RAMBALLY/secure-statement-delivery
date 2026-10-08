package dev.rambally.statements.adapters.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import dev.rambally.statements.application.port.in.UploadStatementUseCase;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.RedemptionOutcome;
import dev.rambally.statements.domain.exception.DomainException;
import dev.rambally.statements.domain.exception.DuplicateTokenHashException;
import dev.rambally.statements.domain.exception.ForbiddenException;
import dev.rambally.statements.domain.exception.IntegrityException;
import dev.rambally.statements.domain.exception.InvalidStatementException;
import dev.rambally.statements.domain.exception.LinkNotFoundException;
import dev.rambally.statements.domain.exception.LinkNotRedeemableException;
import dev.rambally.statements.support.TestPdfs;
import dev.rambally.statements.support.WebSliceTest;

import org.hamcrest.Matchers;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/** Each domain failure maps to its own status, whatever the outer layers do first. */
@WebSliceTest(controllers = AdminStatementController.class)
@Import(DomainExceptionMappingTest.Stubs.class)
class DomainExceptionMappingTest {

    static final class ThrowingUpload implements UploadStatementUseCase {
        DomainException next;

        @Override
        public dev.rambally.statements.domain.StatementId upload(dev.rambally.statements.application.port.in.UploadStatementCommand command) {
            throw next;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Stubs {
        @Bean
        ThrowingUpload uploadStatementUseCase() {
            return new ThrowingUpload();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ThrowingUpload upload;

    static Stream<Arguments> mappings() {
        return Stream.of(
                Arguments.of(new ForbiddenException("admins only"), 403, "Insufficient privileges"),
                Arguments.of(new InvalidStatementException(InvalidStatementException.Reason.TOO_LARGE), 413, "Statement rejected: TOO_LARGE"),
                Arguments.of(new InvalidStatementException(InvalidStatementException.Reason.EMPTY), 400, "Statement rejected: EMPTY"),
                Arguments.of(new LinkNotFoundException(LinkId.newId()), 404, "Link not found"),
                Arguments.of(new LinkNotRedeemableException(RedemptionOutcome.EXPIRED), 404, "Statement not available"),
                Arguments.of(new IntegrityException("tag mismatch on file /secret/path"), 500, "Statement could not be processed"),
                Arguments.of(new DuplicateTokenHashException(), 500, "Please retry"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mappings")
    void maps_each_domain_failure_to_its_status_with_a_fixed_body(DomainException failure, int status, String detail)
            throws Exception {
        upload.next = failure;

        mvc.perform(multipart("/api/admin/statements")
                        .file(new MockMultipartFile("file", "s.pdf", "application/pdf", TestPdfs.minimal()))
                        .param("customerId", "C-1001").param("accountNumber", "1234567890").param("period", "2026-09")
                        .with(jwt().jwt(j -> j.subject("ops-admin")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.instance").value("/api"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("/secret/path"))));
    }
}
