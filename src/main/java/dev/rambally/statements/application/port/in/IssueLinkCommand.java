package dev.rambally.statements.application.port.in;

import dev.rambally.statements.application.Principal;
import dev.rambally.statements.domain.StatementId;

public record IssueLinkCommand(StatementId statementId, Principal actor) {

    public IssueLinkCommand {
        if (statementId == null || actor == null) {
            throw new IllegalArgumentException("issue command is incomplete");
        }
    }
}
