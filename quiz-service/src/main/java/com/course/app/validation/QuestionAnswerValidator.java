package com.course.app.validation;

import com.course.app.dto.QuestionDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class QuestionAnswerValidator implements ConstraintValidator<ValidQuestionAnswer, QuestionDto> {

    @Override
    public boolean isValid(QuestionDto question, ConstraintValidatorContext context) {
        // null object / missing options are reported by other constraints (@Size, @NotBlank)
        if (question == null || question.options() == null) {
            return true;
        }
        int size = question.options().size();
        if (question.correctOption() < size) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                        "correctOption " + question.correctOption() + " is out of range: question has "
                                + size + " options (valid indexes 0.." + (size - 1) + ")")
                .addPropertyNode("correctOption")
                .addConstraintViolation();
        return false;
    }
}
