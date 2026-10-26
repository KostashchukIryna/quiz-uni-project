package com.course.app.async;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Simulates ~1 second of external I/O. */
@Component
public class SimulatedSlowWork implements SlowWork {

    private final Duration delay;

    public SimulatedSlowWork(@Value("${app.async.simulated-delay:1s}") Duration delay) {
        this.delay = delay;
    }

    @Override
    public void perform(String operation) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(operation + " interrupted", e);
        }
    }
}
