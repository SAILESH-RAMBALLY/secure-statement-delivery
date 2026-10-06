package dev.rambally.statements.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import dev.rambally.statements.application.fakes.FixedTokenGenerator;

import org.junit.jupiter.api.Test;

class PublicBaseUrlTest {

    @Test
    void builds_download_url_under_the_base_with_or_without_trailing_slash() {
        var token = FixedTokenGenerator.tokenNumber(1);

        assertThat(new PublicBaseUrl(URI.create("https://statements.example.test")).downloadUrl(token))
                .isEqualTo(URI.create("https://statements.example.test/download/" + token.value()));
        assertThat(new PublicBaseUrl(URI.create("https://statements.example.test/")).downloadUrl(token))
                .isEqualTo(URI.create("https://statements.example.test/download/" + token.value()));
        assertThat(new PublicBaseUrl(URI.create("http://localhost:8080/base")).downloadUrl(token))
                .isEqualTo(URI.create("http://localhost:8080/base/download/" + token.value()));
    }

    @Test
    void rejects_relative_urls_and_urls_with_query_or_fragment() {
        assertThatThrownBy(() -> new PublicBaseUrl(URI.create("/relative"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicBaseUrl(URI.create("https://x.test/?a=b"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicBaseUrl(URI.create("https://x.test/#frag"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicBaseUrl(URI.create("mailto:x@y.test"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicBaseUrl(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
