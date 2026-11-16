package com.course.app.aop;

import com.course.app.dto.QuizDto;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/** AfterReturning advice: only successful calls are audited; a method that throws leaves no audit record. */
@Aspect
@Component
@Order(1002)
public class AuditAspect {

    private final AuditLog auditLog;
    private final Clock clock;

    public AuditAspect(AuditLog auditLog, Clock clock) {
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void audit(JoinPoint joinPoint, Audited audited, Object result) {
        auditLog.record(new AuditEvent(clock.instant(), currentUser(), audited.action(),
                targetId(joinPoint.getArgs(), result)));
    }

    private static UUID targetId(Object[] args, Object result) {
        if (result instanceof QuizDto dto) {
            return dto.id();
        }
        for (Object arg : args) {
            if (arg instanceof UUID id) {
                return id;
            }
        }
        return null;
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "anonymous" : auth.getName();
    }
}
