package com.course.app.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Around advice: decides whether the call may proceed. Over the limit the target method is NOT invoked
 * and a 429 is thrown instead.
 */
@Aspect
@Component
@Order(1000) // after Spring Security's method interceptors, so rejected (403) calls do not consume the quota
public class RateLimitAspect {

    private final Clock clock;
    private final Map<String, Deque<Instant>> calls = new ConcurrentHashMap<>();

    public RateLimitAspect(Clock clock) {
        this.clock = clock;
    }

    @Around("@annotation(rateLimited)")
    public Object limit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        String key = currentUser() + "|" + joinPoint.getSignature().toShortString();
        Instant now = clock.instant();
        Instant windowStart = now.minus(Duration.ofSeconds(rateLimited.perSeconds()));

        Deque<Instant> window = calls.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && !window.peekFirst().isAfter(windowStart)) {
                window.pollFirst();
            }
            if (window.size() >= rateLimited.maxCalls()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Rate limit exceeded: max " + rateLimited.maxCalls() + " calls per "
                                + rateLimited.perSeconds() + "s");
            }
            window.addLast(now);
        }
        return joinPoint.proceed();
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "anonymous" : auth.getName();
    }
}
