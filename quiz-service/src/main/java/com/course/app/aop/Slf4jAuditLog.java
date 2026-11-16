package com.course.app.aop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Slf4jAuditLog implements AuditLog {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    @Override
    public void record(AuditEvent event) {
        AUDIT.info("at={} user={} action={} target={}", event.at(), event.user(), event.action(), event.targetId());
    }
}
