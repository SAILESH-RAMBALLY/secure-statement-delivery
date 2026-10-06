package dev.rambally.statements.application.port.out;

/**
 * How a customer learns about a new link. Carries the real URL so an e-mail or SMS adapter is a true
 * drop-in; the logging adapter used in development is responsible for redacting it.
 */
public interface NotificationPort {

    void linkIssued(LinkIssuedNotification notification);
}
