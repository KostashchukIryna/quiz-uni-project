package com.course.app.aop;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConflictTranslationAspectTest {

    private final ConflictTranslationAspect aspect = new ConflictTranslationAspect();

    @Test
    void translate_integrityViolation_throws409KeepingOriginalCause() {
        // Arrange
        DataIntegrityViolationException original = new DataIntegrityViolationException("duplicate key");

        // Act + Assert
        assertThatThrownBy(() -> aspect.translate(original))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(409);
                    assertThat(e.getCause()).isSameAs(original);
                })
                .hasMessageContaining("constraint violation");
    }
}
