package dev.rambally.statements.application;

/**
 * Runs a best-effort side effect (audit, notification) without letting its failure change the outcome of a
 * use case whose state change is already committed. Adapters own the logging and metrics for their own
 * failures; the application layer has neither.
 */
final class SideEffects {

    private SideEffects() {
    }

    static void quietly(Runnable sideEffect) {
        try {
            sideEffect.run();
        } catch (RuntimeException ignored) {
            // By contract the adapter has already reported this through its own channel.
        }
    }
}
