package dev.rambally.statements.application.port.out;

import java.net.URI;
import java.time.Instant;

import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.LinkId;

public record LinkIssuedNotification(CustomerId customerId, LinkId linkId, URI url, Instant expiresAt, int maxDownloads) {
}
