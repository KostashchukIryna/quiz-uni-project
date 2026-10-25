package com.course.app.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRolesTest {

    @Test
    void fromRealmAccess_rolesPresent_returnsUpperCasedRolePrefixedAuthorities() {
        // Arrange
        Object realmAccess = Map.of("roles", List.of("host", "ADMIN"));

        // Act
        Collection<GrantedAuthority> result = KeycloakRoles.fromRealmAccess(realmAccess);

        // Assert
        assertThat(result).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_HOST", "ROLE_ADMIN");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidRealmAccess")
    void fromRealmAccess_missingOrMalformedClaim_returnsEmptyList(String caseName, Object realmAccess) {
        // Act
        Collection<GrantedAuthority> result = KeycloakRoles.fromRealmAccess(realmAccess);

        // Assert
        assertThat(result).isEmpty();
    }

    static Stream<Object[]> invalidRealmAccess() {
        return Stream.of(
                new Object[]{"null claim", null},
                new Object[]{"not a map", "roles"},
                new Object[]{"map without roles", Map.of("other", 1)},
                new Object[]{"roles not a collection", Map.of("roles", "ADMIN")}
        );
    }
}
