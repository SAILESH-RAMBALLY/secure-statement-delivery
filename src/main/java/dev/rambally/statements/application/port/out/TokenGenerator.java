package dev.rambally.statements.application.port.out;

import dev.rambally.statements.domain.LinkToken;

/** Source of unpredictable link tokens. Driven port so tests can use preset tokens. */
public interface TokenGenerator {

    LinkToken next();
}
