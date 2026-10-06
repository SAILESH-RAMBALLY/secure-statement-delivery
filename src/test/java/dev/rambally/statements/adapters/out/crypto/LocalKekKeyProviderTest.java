package dev.rambally.statements.adapters.out.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import dev.rambally.statements.application.port.out.WrappedKey;
import dev.rambally.statements.domain.exception.IntegrityException;

import org.junit.jupiter.api.Test;

class LocalKekKeyProviderTest {

    private static final byte[] KEK = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] AAD = "dek:some-statement:kek:local".getBytes(StandardCharsets.UTF_8);

    private final LocalKekKeyProvider provider = new LocalKekKeyProvider(KEK, "local");

    @Test
    void rejects_kek_that_is_not_32_bytes() {
        assertThatThrownBy(() -> new LocalKekKeyProvider(new byte[16], "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalKekKeyProvider(null, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalKekKeyProvider(KEK, " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wrap_then_unwrap_returns_same_dek() {
        byte[] dek = new byte[32];
        for (int i = 0; i < 32; i++) {
            dek[i] = (byte) (i * 7);
        }

        WrappedKey wrapped = provider.wrap(dek, AAD);

        assertThat(wrapped.kekId()).isEqualTo("local");
        assertThat(provider.unwrap(wrapped, AAD)).isEqualTo(dek);
    }

    @Test
    void wrapped_dek_is_48_bytes_and_iv_is_12_bytes_and_fresh_each_time() {
        byte[] dek = new byte[32];

        WrappedKey first = provider.wrap(dek, AAD);
        WrappedKey second = provider.wrap(dek, AAD);

        assertThat(first.wrappedDek()).hasSize(48);
        assertThat(first.dekIv()).hasSize(12);
        assertThat(first.dekIv()).isNotEqualTo(second.dekIv());
        assertThat(first.wrappedDek()).isNotEqualTo(second.wrappedDek());
    }

    @Test
    void unwrap_with_different_kek_or_aad_fails() {
        byte[] dek = new byte[32];
        WrappedKey wrapped = provider.wrap(dek, AAD);
        LocalKekKeyProvider other = new LocalKekKeyProvider("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.US_ASCII), "local");

        assertThatThrownBy(() -> other.unwrap(wrapped, AAD)).isInstanceOf(IntegrityException.class);
        assertThatThrownBy(() -> provider.unwrap(wrapped, "dek:other".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IntegrityException.class);
    }

    @Test
    void unwrap_refuses_a_key_wrapped_by_another_kek_id() {
        WrappedKey wrapped = new LocalKekKeyProvider(KEK, "elsewhere").wrap(new byte[32], AAD);

        assertThatThrownBy(() -> provider.unwrap(wrapped, AAD)).isInstanceOf(IntegrityException.class);
    }

    @Test
    void from_config_decodes_base64_and_reports_no_dev_fallback() {
        LocalKekKeyProvider configured = LocalKekKeyProvider.fromConfig(Base64.getEncoder().encodeToString(KEK), "cfg", false);

        assertThat(configured.currentKekId()).isEqualTo("cfg");
        assertThat(configured.usingDevFallback()).isFalse();
    }

    @Test
    void blank_kek_falls_back_to_dev_key_only_when_allowed() {
        LocalKekKeyProvider fallback = LocalKekKeyProvider.fromConfig("", "cfg", true);
        assertThat(fallback.usingDevFallback()).isTrue();
        assertThat(LocalKekKeyProvider.fromConfig(null, "cfg", true).usingDevFallback()).isTrue();

        assertThatThrownBy(() -> LocalKekKeyProvider.fromConfig("", "cfg", false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LocalKekKeyProvider.fromConfig("not base64!", "cfg", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LocalKekKeyProvider.fromConfig(Base64.getEncoder().encodeToString(new byte[16]), "cfg", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configuring_the_dev_key_explicitly_is_detected_and_refused_unless_fallback_is_allowed() {
        LocalKekKeyProvider explicitDev = LocalKekKeyProvider.fromConfig(LocalKekKeyProvider.DEV_KEK_BASE64, "cfg", true);
        assertThat(explicitDev.usingDevFallback()).isTrue();

        assertThatThrownBy(() -> LocalKekKeyProvider.fromConfig(LocalKekKeyProvider.DEV_KEK_BASE64, "cfg", false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("development key");
        assertThatThrownBy(() -> LocalKekKeyProvider.fromConfig(LocalKekKeyProvider.DEV_KEK_BASE64.replace("=", ""), "cfg", false))
                .as("non-canonical spelling of the same bytes").isInstanceOf(IllegalArgumentException.class);
    }
}
