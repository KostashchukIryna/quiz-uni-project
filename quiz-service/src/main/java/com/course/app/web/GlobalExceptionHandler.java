package com.course.app.web;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Turns @PreAuthorize denials into 403 and executor overload into 503, instead of 500. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> denied(AccessDeniedException e) {
        return ResponseEntity.status(403).body(Map.of("error", "Forbidden", "message", e.getMessage()));
    }

    /** Explicit overload decision: the async executor is full -> ask the client to retry later. */
    @ExceptionHandler(TaskRejectedException.class)
    public ResponseEntity<Map<String, String>> overloaded(TaskRejectedException e) {
        return ResponseEntity.status(503)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(Map.of("error", "Service Unavailable",
                        "message", "The server is busy right now, please try again later"));
    }
}
