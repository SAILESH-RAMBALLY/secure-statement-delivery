package dev.rambally.statements.domain;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The download credential: 256 random bits as 43 characters of unpadded base64url. It is never
 * persisted and never logged; {@link #toString()} is redacted so it cannot leak by accident.
 */
public record LinkToken(String value) {

    public static final int LENGTH = 43;
    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{" + LENGTH + "}");

    public LinkToken {
        Invariants.require(value != null && FORMAT.matcher(value).matches(), "token must be 43 base64url characters");
    }

    /** Lenient parse for untrusted input: anything malformed is simply empty, never an exception. */
    public static Optional<LinkToken> parse(String text) {
        if (text == null || !FORMAT.matcher(text).matches()) {
            return Optional.empty();
        }
        return Optional.of(new LinkToken(text));
    }

    public TokenHash hash() {
        return new TokenHash(Sha256.of(value.getBytes(StandardCharsets.UTF_8)).value());
    }

    @Override
    public String toString() {
        return "LinkToken[redacted]";
    }
}
