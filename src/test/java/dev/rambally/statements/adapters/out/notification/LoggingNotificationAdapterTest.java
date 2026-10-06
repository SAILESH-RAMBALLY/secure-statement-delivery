package dev.rambally.statements.adapters.out.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import dev.rambally.statements.application.port.out.LinkIssuedNotification;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.LinkId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingNotificationAdapterTest {

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP-";

    private final LoggingNotificationAdapter adapter = new LoggingNotificationAdapter();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void attach() {
        logs.start();
        ((Logger) LoggerFactory.getLogger(LoggingNotificationAdapter.class)).addAppender(logs);
    }

    @AfterEach
    void detach() {
        ((Logger) LoggerFactory.getLogger(LoggingNotificationAdapter.class)).detachAppender(logs);
    }

    @Test
    void logs_redacted_url_and_never_the_token_or_the_customer_id() {
        LinkId linkId = LinkId.newId();
        adapter.linkIssued(new LinkIssuedNotification(new CustomerId("C-1001"), linkId,
                URI.create("https://statements.example.test:8443/download/" + TOKEN), Instant.parse("2026-10-07T10:00:00Z"), 1));

        assertThat(logs.list).singleElement().satisfies(event -> {
            String message = event.getFormattedMessage();
            assertThat(message).contains(linkId.toString())
                    .contains("https://statements.example.test:8443/download/[redacted]")
                    .doesNotContain(TOKEN)
                    .as("customer identifiers are personal data and stay out of logs").doesNotContain("C-1001");
        });
    }

    @Test
    void redaction_keeps_scheme_host_and_port_only() {
        assertThat(LoggingNotificationAdapter.redact(URI.create("http://localhost:8080/download/" + TOKEN)))
                .isEqualTo("http://localhost:8080/download/[redacted]");
        assertThat(LoggingNotificationAdapter.redact(URI.create("https://x.test/base/download/" + TOKEN)))
                .isEqualTo("https://x.test/download/[redacted]");
    }
}
