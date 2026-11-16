package com.course.app.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleControllersTest {

    @Test
    void adminPing_called_returnsAdminOkStatus() {
        // Act
        Map<String, String> result = new AdminApiController().ping();

        // Assert
        assertThat(result).containsEntry("status", "admin ok");
    }

    @Test
    void publicPing_called_returnsPong() {
        assertThat(new PublicApiController().ping()).startsWith("pong");
    }

    @Test
    void home_called_redirectsToQuizzesUi() {
        assertThat(new HomeController().home()).isEqualTo("redirect:/ui/quizzes");
    }

    @Test
    void denied_accessDenied_returns403WithMessage() {
        // Arrange
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        // Act
        ResponseEntity<Map<String, String>> response = handler.denied(new AccessDeniedException("nope"));

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).containsEntry("error", "Forbidden").containsEntry("message", "nope");
    }
}
