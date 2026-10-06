package dev.rambally.statements.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import dev.rambally.statements.application.port.in.RedeemDownloadLinkUseCase;
import dev.rambally.statements.application.port.in.RequestContext;
import dev.rambally.statements.application.port.in.StatementDownload;
import dev.rambally.statements.domain.RedemptionOutcome;
import dev.rambally.statements.domain.exception.LinkNotRedeemableException;
import dev.rambally.statements.support.TestPdfs;
import dev.rambally.statements.support.WebSliceTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebSliceTest(controllers = DownloadController.class)
@Import(DownloadControllerTest.Stubs.class)
class DownloadControllerTest {

    static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP-";

    static final class StubRedeem implements RedeemDownloadLinkUseCase {
        int invocations;
        String lastRawToken;
        RequestContext lastContext;
        RuntimeException failWith;

        @Override
        public StatementDownload redeem(String rawToken, RequestContext context) {
            invocations++;
            lastRawToken = rawToken;
            lastContext = context;
            if (failWith != null) {
                throw failWith;
            }
            return new StatementDownload("statement-7890-2026-09.pdf", TestPdfs.minimal());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Stubs {
        @Bean
        StubRedeem redeemDownloadLinkUseCase() {
            return new StubRedeem();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    StubRedeem redeem;

    @Test
    void valid_token_returns_200_pdf_with_attachment_server_derived_filename_and_hardened_headers() throws Exception {
        redeem.failWith = null;

        mvc.perform(get("/download/" + TOKEN).header("User-Agent", "curl/8").with(r -> {
                    r.setRemoteAddr("203.0.113.7");
                    return r;
                }))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"statement-7890-2026-09.pdf\""))
                .andExpect(header().longValue("Content-Length", TestPdfs.minimal().length))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(content().bytes(TestPdfs.minimal()));

        assertThat(redeem.lastRawToken).isEqualTo(TOKEN);
        assertThat(redeem.lastContext).isEqualTo(new RequestContext("203.0.113.7", "curl/8"));
    }

    @ParameterizedTest
    @EnumSource(value = RedemptionOutcome.class, names = {"SUCCESS"}, mode = EnumSource.Mode.EXCLUDE)
    void every_redemption_outcome_returns_byte_identical_404_problem_detail(RedemptionOutcome outcome) throws Exception {
        redeem.failWith = new LinkNotRedeemableException(outcome);

        MvcResult result = mvc.perform(get("/download/" + TOKEN)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).isEqualTo(DownloadProblem.NOT_FOUND_BODY);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(headersOf(result.getResponse())).isEqualTo(headersOf(referenceNotFound()));
    }

    private MockHttpServletResponse referenceNotFound() throws Exception {
        redeem.failWith = new LinkNotRedeemableException(RedemptionOutcome.UNKNOWN_TOKEN);
        return mvc.perform(get("/download/" + TOKEN)).andReturn().getResponse();
    }

    private static Map<String, String> headersOf(MockHttpServletResponse response) {
        Map<String, String> headers = new TreeMap<>();
        for (String name : response.getHeaderNames()) {
            headers.put(name, String.join(",", response.getHeaders(name)));
        }
        return new HashMap<>(headers);
    }

    @Test
    void malformed_token_is_passed_to_the_use_case_and_yields_the_same_404() throws Exception {
        redeem.failWith = new LinkNotRedeemableException(RedemptionOutcome.MALFORMED_TOKEN);

        mvc.perform(get("/download/not-a-token"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(DownloadProblem.NOT_FOUND_BODY));

        assertThat(redeem.lastRawToken).isEqualTo("not-a-token");
    }

    @Test
    void head_does_not_invoke_use_case_and_returns_404() throws Exception {
        int before = redeem.invocations;

        mvc.perform(head("/download/" + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        assertThat(redeem.invocations).isEqualTo(before);
    }

    @Test
    void other_methods_return_405_with_allow_get_head_and_no_token_echo() throws Exception {
        int before = redeem.invocations;
        for (HttpMethod method : new HttpMethod[] {HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH}) {
            mvc.perform(request(method, "/download/" + TOKEN))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", "GET, HEAD"))
                    .andExpect(content().string(DownloadProblem.METHOD_NOT_ALLOWED_BODY));
        }
        mvc.perform(options("/download/" + TOKEN))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET, HEAD"));

        assertThat(redeem.invocations).isEqualTo(before);
    }

    @Test
    void unexpected_exception_from_use_case_becomes_the_same_404() throws Exception {
        redeem.failWith = new IllegalStateException("database exploded with token " + TOKEN);

        mvc.perform(get("/download/" + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(content().string(DownloadProblem.NOT_FOUND_BODY));
    }

    @Test
    void an_accept_header_that_excludes_pdf_is_not_a_406_oracle() throws Exception {
        redeem.failWith = null;

        mvc.perform(get("/download/" + TOKEN).header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void any_other_path_under_download_gets_the_identical_404_body() throws Exception {
        int before = redeem.invocations;

        mvc.perform(get("/download/" + TOKEN + "/extra"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(DownloadProblem.NOT_FOUND_BODY));
        mvc.perform(get("/download/"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(DownloadProblem.NOT_FOUND_BODY));

        assertThat(redeem.invocations).isEqualTo(before);
    }

    @Test
    void download_is_reachable_without_authentication() throws Exception {
        redeem.failWith = null;

        mvc.perform(get("/download/" + TOKEN)).andExpect(status().isOk());
    }
}
