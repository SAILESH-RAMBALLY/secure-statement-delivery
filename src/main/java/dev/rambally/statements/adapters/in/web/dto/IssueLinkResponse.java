package dev.rambally.statements.adapters.in.web.dto;

import java.net.URI;
import java.time.Instant;

import dev.rambally.statements.application.port.in.IssuedLink;

public record IssueLinkResponse(String linkId, URI url, Instant expiresAt, int maxDownloads) {

    public static IssueLinkResponse of(IssuedLink issued) {
        return new IssueLinkResponse(issued.linkId().toString(), issued.url(), issued.expiresAt(), issued.maxDownloads());
    }
}
