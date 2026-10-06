package dev.rambally.statements.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;

import org.junit.jupiter.api.Test;

class MetricsConfigTest {

    private final MeterFilter filter = MetricsConfig.collapseDownloadUris();

    private static String uriTag(MeterFilter filter, String uri) {
        Meter.Id id = new Meter.Id("http.server.requests", Tags.of("uri", uri, "method", "GET"), null, null, Meter.Type.TIMER);
        return filter.map(id).getTag("uri");
    }

    @Test
    void collapses_any_raw_download_path_to_the_template() {
        assertThat(uriTag(filter, "/download/abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP-")).isEqualTo("/download/{token}");
        assertThat(uriTag(filter, "/download/not-a-token")).isEqualTo("/download/{token}");
        assertThat(uriTag(filter, "/download/x/y")).isEqualTo("/download/{token}");
    }

    @Test
    void leaves_the_template_and_other_paths_alone() {
        assertThat(uriTag(filter, "/download/{token}")).isEqualTo("/download/{token}");
        assertThat(uriTag(filter, "/api/statements")).isEqualTo("/api/statements");
        assertThat(uriTag(filter, "UNKNOWN")).isEqualTo("UNKNOWN");
    }
}
