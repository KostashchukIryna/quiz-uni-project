package com.course.app.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** No Spring context: the ProceedingJoinPoint is a Mockito mock and the clock is controlled by the test. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitAspectTest {

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @RateLimited(maxCalls = 2, perSeconds = 60)
    void limitedMethod() {
    }

    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private Signature signature;

    private MutableClock clock;
    private RateLimitAspect aspect;
    private RateLimited limit;

    @BeforeEach
    void setUp() throws Throwable {
        clock = new MutableClock();
        aspect = new RateLimitAspect(clock);
        Method m = RateLimitAspectTest.class.getDeclaredMethod("limitedMethod");
        limit = m.getAnnotation(RateLimited.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("QuizService.create(..)");
        when(joinPoint.proceed()).thenReturn("result");
        login("host1");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void login(String user) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, "n"));
    }

    @Test
    void limit_underTheLimit_proceedsAndReturnsTargetResult() throws Throwable {
        // Act
        Object result = aspect.limit(joinPoint, limit);

        // Assert
        assertThat(result).isEqualTo("result");
        verify(joinPoint).proceed();
    }

    @Test
    void limit_callsExceedMax_throws429AndDoesNotProceed() throws Throwable {
        // Arrange
        aspect.limit(joinPoint, limit);
        aspect.limit(joinPoint, limit);

        // Act + Assert
        assertThatThrownBy(() -> aspect.limit(joinPoint, limit))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(429))
                .hasMessageContaining("max 2 calls per 60s");
        verify(joinPoint, times(2)).proceed();
    }

    @Test
    void limit_afterWindowExpires_allowsCallsAgain() throws Throwable {
        // Arrange
        aspect.limit(joinPoint, limit);
        aspect.limit(joinPoint, limit);
        clock.advance(Duration.ofSeconds(61));

        // Act
        Object result = aspect.limit(joinPoint, limit);

        // Assert
        assertThat(result).isEqualTo("result");
        verify(joinPoint, times(3)).proceed();
    }

    @Test
    void limit_differentUsers_haveIndependentQuotas() throws Throwable {
        // Arrange
        aspect.limit(joinPoint, limit);
        aspect.limit(joinPoint, limit);
        login("host2");

        // Act
        Object result = aspect.limit(joinPoint, limit);

        // Assert
        assertThat(result).isEqualTo("result");
    }

    @Test
    void limit_anonymousCaller_isTrackedUnderAnonymousKey() throws Throwable {
        // Arrange
        SecurityContextHolder.clearContext();
        aspect.limit(joinPoint, limit);
        aspect.limit(joinPoint, limit);

        // Act + Assert
        assertThatThrownBy(() -> aspect.limit(joinPoint, limit)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void limit_targetThrows_propagatesExceptionUnchanged() throws Throwable {
        // Arrange
        IllegalStateException boom = new IllegalStateException("boom");
        when(joinPoint.proceed()).thenThrow(boom);

        // Act + Assert
        assertThatThrownBy(() -> aspect.limit(joinPoint, limit)).isSameAs(boom);
    }

    @Test
    void limit_rejectedCall_neverTouchesTarget() throws Throwable {
        // Arrange
        aspect.limit(joinPoint, limit);
        aspect.limit(joinPoint, limit);
        org.mockito.Mockito.clearInvocations(joinPoint);

        // Act
        assertThatThrownBy(() -> aspect.limit(joinPoint, limit)).isInstanceOf(ResponseStatusException.class);

        // Assert
        verify(joinPoint, never()).proceed();
    }
}
