package com.course.app.aop;

import com.course.app.domain.Quiz;
import com.course.app.domain.QuizStatus;
import com.course.app.dto.QuizDto;
import com.course.app.mapper.QuizMapper;
import com.course.app.repository.QuizRepository;
import com.course.app.service.QuizService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Real Spring AOP proxy around QuizService (AspectJProxyFactory - no application context, no database).
 * Demonstrates that advice runs on calls that go through the proxy and is silently skipped on this.method() calls.
 */
class SelfInvocationTest {

    private QuizRepository repo;
    private QuizMapper mapper;
    private AuditLog auditLog;
    private QuizService proxy;

    @BeforeEach
    void setUp() {
        repo = mock(QuizRepository.class);
        mapper = mock(QuizMapper.class);
        auditLog = mock(AuditLog.class);
        Clock clock = Clock.systemUTC();

        AspectJProxyFactory factory = new AspectJProxyFactory(new QuizService(repo, mapper, mock(com.course.app.async.QuizNotificationService.class)));
        factory.setProxyTargetClass(true);
        factory.addAspect(new AuditAspect(auditLog, clock));
        factory.addAspect(new RateLimitAspect(clock));
        factory.addAspect(new ConflictTranslationAspect());
        proxy = factory.getProxy();
    }

    private static QuizDto dto(UUID id) {
        return new QuizDto(id, "title", "d", QuizStatus.DRAFT, null, null, List.of());
    }

    private List<String> auditedActions() {
        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLog, atLeast(0)).record(events.capture());
        return events.getAllValues().stream().map(AuditEvent::action).toList();
    }

    @Test
    void proxy_isAnAopProxyOfQuizService() {
        assertThat(AopUtils.isAopProxy(proxy)).isTrue();
    }

    @Test
    void create_calledThroughProxy_adviceRunsAndAuditEventIsRecorded() {
        // Arrange
        Quiz entity = new Quiz();
        when(mapper.toEntity(any())).thenReturn(entity);
        when(repo.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(dto(UUID.randomUUID()));

        // Act
        proxy.create(dto(null), "host1");

        // Assert
        assertThat(auditedActions()).containsExactly("CREATE_QUIZ");
    }

    @Test
    void duplicate_callsCreateViaThis_createAdviceIsSkippedSilently() {
        // Arrange
        UUID id = UUID.randomUUID();
        Quiz entity = new Quiz();
        when(repo.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(dto(id));
        when(mapper.toEntity(any())).thenReturn(entity);
        when(repo.save(entity)).thenReturn(entity);

        // Act
        proxy.duplicate(id, "host1");

        // Assert: the copy WAS created (repo.save ran) ...
        verify(repo).save(entity);
        // ... but only the outer, proxy-entered method was audited; create()'s own @Audited never fired
        assertThat(auditedActions()).containsExactly("DUPLICATE_QUIZ");
    }

    @Test
    void create_repositoryIntegrityViolation_translatedTo409ThroughProxy() {
        // Arrange
        Quiz entity = new Quiz();
        when(mapper.toEntity(any())).thenReturn(entity);
        when(repo.save(entity)).thenThrow(new DataIntegrityViolationException("dup"));

        // Act + Assert
        assertThatThrownBy(() -> proxy.create(dto(null), "host1"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(auditedActions()).isEmpty();
    }

    @Test
    void create_overRateLimitThroughProxy_isRejectedButSelfInvokedCreateIsNot() {
        // Arrange
        UUID id = UUID.randomUUID();
        Quiz entity = new Quiz();
        when(mapper.toEntity(any())).thenReturn(entity);
        when(repo.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(dto(id));
        when(repo.findById(id)).thenReturn(Optional.of(entity));
        for (int i = 0; i < 10; i++) {
            proxy.create(dto(null), "host1");   // exhausts the 10-calls quota through the proxy
        }

        // Act + Assert: external call is now blocked ...
        assertThatThrownBy(() -> proxy.create(dto(null), "host1"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(429));
        // ... while the same method reached through this.create() inside duplicate() still works
        assertThat(proxy.duplicate(id, "host1")).isNotNull();
    }
}
