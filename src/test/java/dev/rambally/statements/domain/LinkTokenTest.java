package dev.rambally.statements.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LinkTokenTest {

    private static final String VALID = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP-";

    @Test
    void parse_accepts_43_char_base64url_tokens() {
        assertThat(LinkToken.parse(VALID)).isPresent();
        assertThat(LinkToken.parse(VALID).orElseThrow().value()).isEqualTo(VALID);
        assertThat(LinkToken.parse("_".repeat(43))).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP-=",
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO+/", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO ."})
    void parse_rejects_tokens_that_are_not_43_char_base64url_and_returns_empty(String value) {
        assertThat(LinkToken.parse(value)).isEmpty();
    }

    @Test
    void parse_of_null_is_empty_and_constructor_rejects_invalid() {
        assertThat(LinkToken.parse(null)).isEmpty();
        assertThatThrownBy(() -> new LinkToken("nope")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hash_is_sha256_of_utf8_bytes() {
        // SHA-256("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP-") computed independently.
        TokenHash hash = LinkToken.parse(VALID).orElseThrow().hash();

        assertThat(hash.value()).isEqualTo(Sha256.of(VALID.getBytes(java.nio.charset.StandardCharsets.UTF_8)).value());
        assertThat(hash.value()).hasSize(32);
    }

    @Test
    void to_string_is_redacted() {
        LinkToken token = LinkToken.parse(VALID).orElseThrow();

        assertThat(token.toString()).isEqualTo("LinkToken[redacted]").doesNotContain(VALID);
    }

    @Test
    void token_hash_equality_is_by_content_and_prefix_is_8_hex_chars() {
        TokenHash a = LinkToken.parse(VALID).orElseThrow().hash();
        TokenHash b = LinkToken.parse(VALID).orElseThrow().hash();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a.prefix()).hasSize(8).matches("[0-9a-f]{8}");
        assertThat(Sha256.of(VALID.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hex()).startsWith(a.prefix());
        assertThatThrownBy(() -> new TokenHash(new byte[16])).isInstanceOf(IllegalArgumentException.class);
    }
}
