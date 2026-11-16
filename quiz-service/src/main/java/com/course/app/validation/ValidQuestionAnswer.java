package com.course.app.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint for a question: {@code correctOption} must be an index of an existing entry in
 * {@code options}. Validated by {@link QuestionAnswerValidator}, which Bean Validation instantiates itself.
 */
@Documented
@Constraint(validatedBy = QuestionAnswerValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidQuestionAnswer {

    String message() default "correctOption must point to an existing option";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
