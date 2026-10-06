package dev.rambally.statements.application.port.in;

import java.net.URI;
import java.time.Instant;

import dev.rambally.statements.domain.LinkId;

/** The only place the plaintext token leaves the process: embedded in {@code url}. */
public record IssuedLink(LinkId linkId, URI url, Instant expiresAt, int maxDownloads) {
}
