package com.course.app.aop;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(Instant at, String user, String action, UUID targetId) {
}
