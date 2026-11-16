package com.course.app.async;

/**
 * The "slow part" of an async operation (mail server, PDF engine, remote analytics API). Kept behind an interface so
 * the production implementation can simulate latency while tests control timing without any sleeping.
 */
public interface SlowWork {

    void perform(String operation);
}
