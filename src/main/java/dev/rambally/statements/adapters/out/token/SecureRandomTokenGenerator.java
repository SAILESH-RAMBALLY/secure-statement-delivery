package dev.rambally.statements.adapters.out.token;

import java.security.SecureRandom;
import java.util.Base64;

import dev.rambally.statements.application.port.out.TokenGenerator;
import dev.rambally.statements.domain.LinkToken;

/** 32 bytes from SecureRandom, base64url without padding: 43 characters, 256 bits of entropy. */
public final class SecureRandomTokenGenerator implements TokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    @Override
    public LinkToken next() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return new LinkToken(encoder.encodeToString(bytes));
    }
}
