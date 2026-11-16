package com.course.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserAuthenticationConverterTest {

    private final CurrentUserAuthenticationConverter converter = new CurrentUserAuthenticationConverter();

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T01:00:00Z"),
                Map.of("alg", "none"), claims);
    }

    @Test
    void convert_tokenWithRoles_usesUsernameAsNameAndMapsAuthorities() {
        // Arrange
        Jwt jwt = jwt(Map.of("preferred_username", "admin1",
                "realm_access", Map.of("roles", List.of("HOST", "ADMIN"))));

        // Act
        AbstractAuthenticationToken auth = converter.convert(jwt);

        // Assert
        assertThat(auth.getName()).isEqualTo("admin1");
        assertThat(auth.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_HOST", "ROLE_ADMIN");
    }

    @Test
    void convert_tokenWithoutRealmAccess_hasNoAuthorities() {
        // Arrange
        Jwt jwt = jwt(Map.of("preferred_username", "nobody"));

        // Act
        AbstractAuthenticationToken auth = converter.convert(jwt);

        // Assert
        assertThat(auth.getAuthorities()).isEmpty();
        assertThat(auth.getName()).isEqualTo("nobody");
    }
}
