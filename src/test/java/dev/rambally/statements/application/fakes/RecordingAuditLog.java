package dev.rambally.statements.application.fakes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.rambally.statements.application.port.out.AuditLog;
import dev.rambally.statements.domain.AuditEvent;
import dev.rambally.statements.domain.AuditEventType;
import dev.rambally.statements.domain.RedemptionOutcome;

public final class RecordingAuditLog implements AuditLog {

    private final List<AuditEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void record(AuditEvent event) {
        events.add(event);
    }

    public List<AuditEvent> events() {
        return List.copyOf(events);
    }

    public List<AuditEvent> ofType(AuditEventType type) {
        return events.stream().filter(e -> e.type() == type).toList();
    }

    public List<RedemptionOutcome> redemptionOutcomes() {
        return events.stream().filter(e -> e.type() == AuditEventType.REDEMPTION).map(AuditEvent::outcome).toList();
    }
}
