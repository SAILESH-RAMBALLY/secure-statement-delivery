package dev.rambally.statements.application.port.in;

import java.time.Instant;

import dev.rambally.statements.domain.DownloadLink;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.LinkStatus;

public record LinkSummary(LinkId linkId, LinkStatus status, Instant issuedAt, Instant expiresAt, int maxDownloads,
        int downloadCount) {

    public static LinkSummary of(DownloadLink link, Instant now) {
        return new LinkSummary(link.id(), link.status(now), link.issuedAt(), link.expiresAt(), link.maxDownloads(),
                link.downloadCount());
    }
}
