package dev.rambally.statements.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import dev.rambally.statements.application.Principal;
import dev.rambally.statements.application.port.in.IssueDownloadLinkUseCase;
import dev.rambally.statements.application.port.in.IssueLinkCommand;
import dev.rambally.statements.application.port.in.IssuedLink;
import dev.rambally.statements.application.port.in.LinkSummary;
import dev.rambally.statements.application.port.in.ListLinksUseCase;
import dev.rambally.statements.application.port.in.ListStatementsUseCase;
import dev.rambally.statements.application.port.in.StatementSummary;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.LinkStatus;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.StatementPeriod;
import dev.rambally.statements.domain.exception.StatementNotFoundException;
import dev.rambally.statements.support.WebSliceTest;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebSliceTest(controllers = StatementController.class)
@Import(StatementControllerTest.Stubs.class)
class StatementControllerTest {

    static final StatementId OWNED = StatementId.of("550e8400-e29b-41d4-a716-446655440000");
    static final LinkId LINK = LinkId.of("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP-";

    static final class StubIssue implements IssueDownloadLinkUseCase {
        IssueLinkCommand last;

        @Override
        public IssuedLink issue(IssueLinkCommand command) {
            last = command;
            if (!command.statementId().equals(OWNED) || !command.actor().customerId().equals(new CustomerId("C-1001"))) {
                throw new StatementNotFoundException(command.statementId());
            }
            return new IssuedLink(LINK, URI.create("https://statements.example.test/download/" + TOKEN),
                    Instant.parse("2026-10-07T10:00:00Z"), 1);
        }
    }

    static final class StubList implements ListStatementsUseCase {
        Principal last;

        @Override
        public List<StatementSummary> listFor(Principal actor) {
            last = actor;
            return List.of(new StatementSummary(OWNED, "******7890", StatementPeriod.parse("2026-09"), 2048,
                    Instant.parse("2026-10-01T08:00:00Z")));
        }
    }

    static final class StubLinks implements ListLinksUseCase {
        @Override
        public List<LinkSummary> linksFor(StatementId statementId, Principal actor) {
            if (!statementId.equals(OWNED) || !actor.customerId().equals(new CustomerId("C-1001"))) {
                throw new StatementNotFoundException(statementId);
            }
            return List.of(new LinkSummary(LINK, LinkStatus.EXHAUSTED, Instant.parse("2026-10-06T10:00:00Z"),
                    Instant.parse("2026-10-07T10:00:00Z"), 1, 1));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Stubs {
        @Bean
        StubIssue issueDownloadLinkUseCase() {
            return new StubIssue();
        }

        @Bean
        StubLinks listLinksUseCase() {
            return new StubLinks();
        }

        @Bean
        StubList listStatementsUseCase() {
            return new StubList();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    StubIssue issue;

    @Autowired
    StubList list;

    @org.junit.jupiter.api.BeforeEach
    void resetStubs() {
        issue.last = null;
        list.last = null;
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor customer(String subject) {
        return jwt().jwt(j -> j.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    @Test
    void issue_link_returns_201_with_absolute_url() throws Exception {
        mvc.perform(post("/api/statements/" + OWNED + "/links").with(customer("C-1001")))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.linkId").value(LINK.toString()))
                .andExpect(jsonPath("$.url").value("https://statements.example.test/download/" + TOKEN))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-07T10:00:00Z"))
                .andExpect(jsonPath("$.maxDownloads").value(1));

        assertThat(issue.last.actor()).isEqualTo(new Principal(new CustomerId("C-1001"), false));
    }

    @Test
    void issue_link_for_not_owned_statement_is_404_identical_to_unknown() throws Exception {
        mvc.perform(post("/api/statements/" + OWNED + "/links").with(customer("C-2002")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Statement not found"))
                .andExpect(jsonPath("$.instance").value("/api"));

        mvc.perform(post("/api/statements/" + StatementId.newId() + "/links").with(customer("C-1001")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Statement not found"));
    }

    @Test
    void issue_link_with_malformed_id_is_400_and_unauthenticated_is_401() throws Exception {
        mvc.perform(post("/api/statements/not-a-uuid/links").with(customer("C-1001")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(Matchers.not(Matchers.containsString("not-a-uuid"))));

        mvc.perform(post("/api/statements/" + OWNED + "/links"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_returns_only_subjects_statements_with_masked_account() throws Exception {
        mvc.perform(get("/api/statements").with(customer("C-1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statementId").value(OWNED.toString()))
                .andExpect(jsonPath("$[0].accountNumber").value("******7890"))
                .andExpect(jsonPath("$[0].period").value("2026-09"))
                .andExpect(jsonPath("$[0].sizeBytes").value(2048));

        assertThat(list.last.customerId()).isEqualTo(new CustomerId("C-1001"));
    }

    @Test
    void list_rejects_a_customer_id_query_parameter_instead_of_ignoring_it() throws Exception {
        mvc.perform(get("/api/statements").param("customerId", "C-2002").with(customer("C-1001")))
                .andExpect(status().isBadRequest());

        assertThat(list.last).isNull();
    }

    @Test
    void list_treats_an_empty_customer_id_parameter_as_absent() throws Exception {
        // Swagger UI sends `customerId=` for a touched-then-cleared field; that is not an attempt to impersonate.
        mvc.perform(get("/api/statements").param("customerId", "").with(customer("C-1001")))
                .andExpect(status().isOk());

        assertThat(list.last.customerId()).isEqualTo(new CustomerId("C-1001"));
    }

    @Test
    void list_links_returns_status_and_counts_only_and_404_for_not_owned() throws Exception {
        mvc.perform(get("/api/statements/" + OWNED + "/links").with(customer("C-1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].linkId").value(LINK.toString()))
                .andExpect(jsonPath("$[0].status").value("EXHAUSTED"))
                .andExpect(jsonPath("$[0].downloadCount").value(1))
                .andExpect(jsonPath("$[0].maxDownloads").value(1))
                .andExpect(content().string(Matchers.not(Matchers.containsString("hash"))));

        mvc.perform(get("/api/statements/" + OWNED + "/links").with(customer("C-2002")))
                .andExpect(status().isNotFound());
    }
}
