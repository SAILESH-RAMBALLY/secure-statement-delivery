package dev.rambally.statements.application.fakes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import dev.rambally.statements.application.port.out.TokenGenerator;
import dev.rambally.statements.domain.LinkToken;

/** Hands out preset tokens in order, so tests know exactly which token a link was issued with. */
public final class FixedTokenGenerator implements TokenGenerator {

    private final Deque<LinkToken> queue = new ArrayDeque<>();

    public FixedTokenGenerator(LinkToken... tokens) {
        queue.addAll(List.of(tokens));
    }

    public static LinkToken tokenNumber(int n) {
        String digit = Integer.toString(n % 10);
        return new LinkToken(digit.repeat(43));
    }

    @Override
    public LinkToken next() {
        if (queue.isEmpty()) {
            throw new IllegalStateException("no preset tokens left");
        }
        return queue.removeFirst();
    }
}
