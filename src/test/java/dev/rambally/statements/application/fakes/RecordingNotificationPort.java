package dev.rambally.statements.application.fakes;

import java.util.ArrayList;
import java.util.List;

import dev.rambally.statements.application.port.out.LinkIssuedNotification;
import dev.rambally.statements.application.port.out.NotificationPort;

public final class RecordingNotificationPort implements NotificationPort {

    private final List<LinkIssuedNotification> sent = new ArrayList<>();

    @Override
    public void linkIssued(LinkIssuedNotification notification) {
        sent.add(notification);
    }

    public List<LinkIssuedNotification> sent() {
        return List.copyOf(sent);
    }
}
