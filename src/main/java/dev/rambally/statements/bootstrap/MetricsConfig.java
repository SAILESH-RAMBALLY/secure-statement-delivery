package dev.rambally.statements.bootstrap;

import io.micrometer.core.instrument.config.MeterFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defence in depth for the metrics endpoint: the download handler uses a path variable, so Micrometer
 * already tags requests with the template; this filter guarantees a raw token can never appear as a
 * {@code uri} tag value even if a request misses the handler (404, 405, filter rejections).
 */
@Configuration
public class MetricsConfig {

    static final String DOWNLOAD_TEMPLATE = "/download/{token}";

    @Bean
    MeterFilter downloadUriMeterFilter() {
        return collapseDownloadUris();
    }

    static MeterFilter collapseDownloadUris() {
        return MeterFilter.replaceTagValues("uri",
                uri -> uri.startsWith("/download/") && !uri.equals(DOWNLOAD_TEMPLATE) ? DOWNLOAD_TEMPLATE : uri);
    }
}
