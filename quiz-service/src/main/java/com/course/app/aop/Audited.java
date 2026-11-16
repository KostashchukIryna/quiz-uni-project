package com.course.app.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Records an audit event after the annotated method returns successfully. Handled by AuditAspect. */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** Business action name written to the audit trail, e.g. CREATE_QUIZ. */
    String action();
}
