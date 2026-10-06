package dev.rambally.statements.application.port.in;

/** Who asked, for the audit trail. Either value may be absent. */
public record RequestContext(String clientIp, String userAgent) {
}
