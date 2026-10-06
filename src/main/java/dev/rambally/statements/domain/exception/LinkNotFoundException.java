package dev.rambally.statements.domain.exception;

import dev.rambally.statements.domain.LinkId;

/** Unknown link, or one the caller does not own: deliberately indistinguishable. */
public final class LinkNotFoundException extends DomainException {

    private final LinkId linkId;

    public LinkNotFoundException(LinkId linkId) {
        super("link not found: " + linkId);
        this.linkId = linkId;
    }

    public LinkId linkId() {
        return linkId;
    }
}
