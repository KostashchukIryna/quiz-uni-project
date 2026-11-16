package com.course.app.aop;

/** Destination of audit events (kept as an interface so it can be mocked and replaced, e.g. by a DB table). */
public interface AuditLog {

    void record(AuditEvent event);
}
