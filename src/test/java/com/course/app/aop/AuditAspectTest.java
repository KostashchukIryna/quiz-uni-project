package com.course.app.aop;

import com.course.app.domain.QuizStatus;
import com.course.app.dto.QuizDto;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    private static final Instant NOW = Instant.parse("2026-02-03T04:05:06Z");

    @Audited(action = "SAMPLE")
    void audited() {
    }

    @Mock
    private AuditLog auditLog;
    @Mock
    private JoinPoint joinPoint;

    private final AuditAspectTestSupport support = new AuditAspectTestSupport();

    static class AuditAspectTestSupport {
        Audited annotation() throws Exception {
            Method m = AuditAspectTest.class.getDeclaredMethod("audited");
            return m.getAnnotation(Audited.class);
        }
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private AuditAspect aspect() {
        return new AuditAspect(auditLog, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void audit_methodReturnedQuizDto_recordsUserActionTimeAndResultId() throws Exception {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("host1", "n"));
        UUID id = UUID.randomUUID();
        QuizDto result = new QuizDto(id, "t", null, QuizStatus.DRAFT, "host1", null, List.of());
        when(joinPoint.getArgs()).thenReturn(new Object[]{result});

        // Act
        aspect().audit(joinPoint, support.annotation(), result);

        // Assert
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLog).record(event.capture());
        assertThat(event.getValue()).isEqualTo(new AuditEvent(NOW, "host1", "SAMPLE", id));
    }

    @Test
    void audit_voidMethodWithUuidArgument_usesArgumentAsTarget() throws Exception {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin1", "n"));
        UUID id = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[]{"ignored", id});

        // Act
        aspect().audit(joinPoint, support.annotation(), null);

        // Assert
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLog).record(event.capture());
        assertThat(event.getValue().targetId()).isEqualTo(id);
        assertThat(event.getValue().user()).isEqualTo("admin1");
    }

    @Test
    void audit_noAuthenticationAndNoIdentifiableTarget_recordsAnonymousWithNullTarget() throws Exception {
        // Arrange
        when(joinPoint.getArgs()).thenReturn(new Object[]{"x"});

        // Act
        aspect().audit(joinPoint, support.annotation(), "plain");

        // Assert
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLog).record(event.capture());
        assertThat(event.getValue().user()).isEqualTo("anonymous");
        assertThat(event.getValue().targetId()).isNull();
    }
}
