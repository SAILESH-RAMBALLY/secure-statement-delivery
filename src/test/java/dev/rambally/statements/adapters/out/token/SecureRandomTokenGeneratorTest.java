package dev.rambally.statements.adapters.out.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import dev.rambally.statements.domain.LinkToken;

import org.junit.jupiter.api.Test;

class SecureRandomTokenGeneratorTest {

    private final SecureRandomTokenGenerator generator = new SecureRandomTokenGenerator();

    @Test
    void generates_43_char_base64url_tokens_without_padding() {
        LinkToken token = generator.next();

        assertThat(token.value()).hasSize(43).matches("[A-Za-z0-9_-]{43}").doesNotContain("=");
    }

    @Test
    void one_thousand_tokens_are_distinct() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(generator.next().value());
        }
        assertThat(seen).hasSize(1000);
    }
}
