package dev.rambally.statements.application;

import java.net.URI;

import dev.rambally.statements.domain.LinkToken;

/**
 * The externally visible base of this service, from configuration. Link URLs are built from it, never
 * from the Host header, so a request with a forged Host cannot make the service mint links to elsewhere.
 */
public record PublicBaseUrl(URI value) {

    public PublicBaseUrl {
        if (value == null || !value.isAbsolute() || value.getHost() == null) {
            throw new IllegalArgumentException("public base URL must be absolute with a host");
        }
        if (value.getRawQuery() != null || value.getRawFragment() != null) {
            throw new IllegalArgumentException("public base URL must not have a query or fragment");
        }
        String scheme = value.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("public base URL must be http or https");
        }
    }

    public URI downloadUrl(LinkToken token) {
        String base = value.toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + "/download/" + token.value());
    }
}
