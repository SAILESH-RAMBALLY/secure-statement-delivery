package dev.rambally.statements.adapters.in.web.dto;

import java.time.Instant;

import dev.rambally.statements.application.port.in.LinkSummary;

public record LinkSummaryResponse(String linkId, String status, Instant issuedAt, Instant expiresAt, int maxDownloads,
        int downloadCount) {

    public static LinkSummaryResponse of(LinkSummary s) {
        return new LinkSummaryResponse(s.linkId().toString(), s.status().name(), s.issuedAt(), s.expiresAt(),
                s.maxDownloads(), s.downloadCount());
    }
}
