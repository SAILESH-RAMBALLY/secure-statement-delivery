package dev.rambally.statements.bootstrap;

import java.util.List;

import dev.rambally.statements.adapters.in.web.DownloadConcurrencyFilter;
import dev.rambally.statements.adapters.in.web.JwtPrincipalResolver;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new JwtPrincipalResolver());
    }

    /** Runs after the security chain (order -100) so saturation responses still carry the hardened headers. */
    @Bean
    FilterRegistrationBean<DownloadConcurrencyFilter> downloadConcurrencyFilter(AppProperties properties, MeterRegistry registry) {
        FilterRegistrationBean<DownloadConcurrencyFilter> registration = new FilterRegistrationBean<>(
                new DownloadConcurrencyFilter(properties.download().maxConcurrent(), registry));
        registration.addUrlPatterns("/download/*");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }
}
