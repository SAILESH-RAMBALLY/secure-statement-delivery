package dev.rambally.statements.application.port.in;

import java.util.List;

import dev.rambally.statements.application.Principal;

/** The caller's own statements, newest period first. Identity comes from the principal, never from input. */
public interface ListStatementsUseCase {

    List<StatementSummary> listFor(Principal actor);
}
