package com.course.app.aop;

import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * AfterThrowing advice: replaces a raw persistence exception (which would surface as an opaque 500)
 * with a ResponseStatusException(409). Only DataIntegrityViolationException triggers it.
 */
@Aspect
@Component
@Order(1001)
public class ConflictTranslationAspect {

    @AfterThrowing(pointcut = "@annotation(com.course.app.aop.TranslateConflict)", throwing = "ex")
    public void translate(DataIntegrityViolationException ex) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Request conflicts with existing data (constraint violation)", ex);
    }
}
