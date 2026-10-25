package com.course.app.web;

import com.course.app.web.annotation.CurrentUsername;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUsernameArgumentResolverTest {

    private final CurrentUsernameArgumentResolver resolver = new CurrentUsernameArgumentResolver();

    @SuppressWarnings("unused")
    static class Sample {
        void annotated(@CurrentUsername String user) {
        }

        void notAnnotated(String user) {
        }

        void annotatedWrongType(@CurrentUsername Integer user) {
        }
    }

    private static MethodParameter param(String method, Class<?> type) throws NoSuchMethodException {
        Method m = Sample.class.getDeclaredMethod(method, type);
        return new MethodParameter(m, 0);
    }

    @Test
    void supportsParameter_annotatedString_returnsTrue() throws Exception {
        assertThat(resolver.supportsParameter(param("annotated", String.class))).isTrue();
    }

    @Test
    void supportsParameter_withoutAnnotation_returnsFalse() throws Exception {
        assertThat(resolver.supportsParameter(param("notAnnotated", String.class))).isFalse();
    }

    @Test
    void supportsParameter_annotatedButNotString_returnsFalse() throws Exception {
        assertThat(resolver.supportsParameter(param("annotatedWrongType", Integer.class))).isFalse();
    }

    @Test
    void resolveArgument_authenticatedRequest_returnsPrincipalName() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        Principal principal = () -> "host1";
        request.setUserPrincipal(principal);

        // Act
        Object value = resolver.resolveArgument(param("annotated", String.class), null,
                new ServletWebRequest(request), null);

        // Assert
        assertThat(value).isInstanceOf(String.class).isEqualTo("host1");
    }

    @Test
    void resolveArgument_anonymousRequest_throwsUnauthorized() throws Exception {
        // Arrange
        ServletWebRequest anonymous = new ServletWebRequest(new MockHttpServletRequest());
        MethodParameter parameter = param("annotated", String.class);

        // Act + Assert
        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, anonymous, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
    }
}
