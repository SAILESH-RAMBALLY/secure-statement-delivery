package dev.rambally.statements.support;

import dev.rambally.statements.bootstrap.AppProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
public class WebSliceTestConfig {
}
