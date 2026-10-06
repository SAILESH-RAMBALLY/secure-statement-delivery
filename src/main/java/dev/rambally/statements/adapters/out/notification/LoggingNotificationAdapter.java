package dev.rambally.statements.adapters.out.notification;

import java.net.URI;

import dev.rambally.statements.application.port.out.LinkIssuedNotification;
import dev.rambally.statements.application.port.out.NotificationPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development delivery channel: logs that a link was issued, with the token redacted. An e-mail or SMS
 * adapter implementing the same port would receive the real URL.
 */
public final class LoggingNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAdapter.class);

    @Override
    public void linkIssued(LinkIssuedNotification n) {
        log.info("Download link {} for customer {} delivered to {} (expires {}, maxDownloads {})",
                n.linkId(), n.customerId(), redact(n.url()), n.expiresAt(), n.maxDownloads());
    }

    /** scheme://host[:port]/download/[redacted] */
    static String redact(URI url) {
        StringBuilder sb = new StringBuilder(url.getScheme()).append("://").append(url.getHost());
        if (url.getPort() != -1) {
            sb.append(':').append(url.getPort());
        }
        return sb.append("/download/[redacted]").toString();
    }
}
