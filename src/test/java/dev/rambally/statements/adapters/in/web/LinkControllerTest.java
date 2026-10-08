package dev.rambally.statements.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.rambally.statements.application.Principal;
import dev.rambally.statements.application.port.in.RevokeDownloadLinkUseCase;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.exception.LinkNotFoundException;
import dev.rambally.statements.support.WebSliceTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebSliceTest(controllers = LinkController.class)
@Import(LinkControllerTest.Stubs.class)
class LinkControllerTest {

    static final LinkId OWNED = LinkId.of("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    /** Owns exactly one link, for C-1001; admins may revoke anything. */
    static final class StubRevoke implements RevokeDownloadLinkUseCase {
        LinkId lastRevoked;
        Principal lastActor;

        @Override
        public void revoke(LinkId linkId, Principal actor) {
            if (!linkId.equals(OWNED) || !actor.mayAccess(new CustomerId("C-1001"))) {
                throw new LinkNotFoundException(linkId);
            }
            lastRevoked = linkId;
            lastActor = actor;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Stubs {
        @Bean
        StubRevoke revokeDownloadLinkUseCase() {
            return new StubRevoke();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    StubRevoke revoke;

    @BeforeEach
    void reset() {
        revoke.lastRevoked = null;
        revoke.lastActor = null;
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor as(
            String subject, String role) {
        return jwt().jwt(j -> j.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void the_owner_revokes_and_gets_204() throws Exception {
        mvc.perform(delete("/api/links/" + OWNED).with(as("C-1001", "CUSTOMER"))).andExpect(status().isNoContent());

        assertThat(revoke.lastRevoked).isEqualTo(OWNED);
        assertThat(revoke.lastActor).isEqualTo(new Principal(new CustomerId("C-1001"), false));
    }

    @Test
    void an_admin_may_revoke_a_customers_link() throws Exception {
        mvc.perform(delete("/api/links/" + OWNED).with(as("ops-admin", "ADMIN"))).andExpect(status().isNoContent());

        assertThat(revoke.lastActor.admin()).isTrue();
    }

    @Test
    void another_customer_gets_the_same_404_as_an_unknown_link() throws Exception {
        mvc.perform(delete("/api/links/" + OWNED).with(as("C-2002", "CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Link not found"));
        mvc.perform(delete("/api/links/" + LinkId.newId()).with(as("C-1001", "CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Link not found"));

        assertThat(revoke.lastRevoked).isNull();
    }

    @Test
    void a_malformed_id_is_400_without_echoing_it_and_no_token_is_401() throws Exception {
        mvc.perform(delete("/api/links/not-a-uuid").with(as("C-1001", "CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("not-a-uuid"))));
        mvc.perform(delete("/api/links/" + OWNED)).andExpect(status().isUnauthorized());
    }
}
