package com.course.app.validation;

import com.course.app.dto.QuestionDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain Bean Validation bootstrap: the validator class is discovered via @Constraint, never instantiated here. */
class ValidQuestionAnswerTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static QuestionDto question(int correctOption, List<String> options) {
        return new QuestionDto(null, "text", 15, 100, correctOption, options);
    }

    private static Set<ConstraintViolation<QuestionDto>> answerViolations(QuestionDto dto) {
        return validator.validate(dto);
    }

    @Test
    void validate_correctOptionInsideOptions_hasNoViolations() {
        // Arrange
        QuestionDto dto = question(1, List.of("a", "b", "c"));

        // Act
        Set<ConstraintViolation<QuestionDto>> violations = answerViolations(dto);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void validate_lastValidIndex_hasNoViolations() {
        assertThat(answerViolations(question(2, List.of("a", "b", "c")))).isEmpty();
    }

    @Test
    void validate_correctOptionEqualsOptionsSize_reportsExplicitMessageOnCorrectOption() {
        // Arrange
        QuestionDto dto = question(3, List.of("a", "b", "c"));

        // Act
        Set<ConstraintViolation<QuestionDto>> violations = answerViolations(dto);

        // Assert
        assertThat(violations).hasSize(1);
        ConstraintViolation<QuestionDto> v = violations.iterator().next();
        assertThat(v.getPropertyPath().toString()).isEqualTo("correctOption");
        assertThat(v.getMessage())
                .contains("correctOption 3", "3 options", "0..2")
                .isNotEqualToIgnoringCase("invalid");
    }

    @Test
    void validate_correctOptionFarOutOfRange_reportsViolation() {
        assertThat(answerViolations(question(99, List.of("a", "b")))).hasSize(1);
    }

    @Test
    void validate_nullOptions_doesNotReportThisConstraint() {
        // Arrange: missing options are the job of @Size, not of this constraint
        QuestionDto dto = question(0, null);

        // Act
        Set<ConstraintViolation<QuestionDto>> violations = answerViolations(dto);

        // Assert
        assertThat(violations).noneMatch(v -> v.getMessage().contains("out of range"));
    }

    @Test
    void validate_nullQuestion_isConsideredValidByTheValidator() {
        // Arrange
        QuestionAnswerValidator validator = new QuestionAnswerValidator();

        // Act + Assert
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
