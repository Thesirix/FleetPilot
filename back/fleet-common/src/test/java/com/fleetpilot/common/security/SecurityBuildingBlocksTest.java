package com.fleetpilot.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityBuildingBlocksTest {

    private static final UUID COMPANY = UUID.fromString("474a1f7d-c59a-3321-a0a2-729ec42501d5");

    private static Jwt token(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("4b2c")
                .issuedAt(Instant.parse("2026-10-14T08:00:00Z")).expiresAt(Instant.parse("2026-10-14T08:10:00Z"));
        claims.forEach(builder::claim);
        return builder.build();
    }

    private static void login(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new KeycloakJwtConverter().convert(jwt));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void keycloak_realm_roles_become_spring_roles() {
        AbstractAuthenticationToken authentication = new KeycloakJwtConverter().convert(token(Map.of(
                "preferred_username", "m.roux@neolia-energies.fr",
                "realm_access", Map.of("roles", List.of("client-manager", "offline_access")))));

        assertThat(authentication.getName()).isEqualTo("m.roux@neolia-energies.fr");
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_client-manager", "ROLE_offline_access");
    }

    @Test
    void a_token_without_roles_grants_nothing() {
        assertThat(new KeycloakJwtConverter().convert(token(Map.of("scope", "openid"))).getAuthorities()).isEmpty();
        assertThat(new KeycloakJwtConverter().convert(token(Map.of("realm_access", Map.of()))).getAuthorities()).isEmpty();
    }

    @Test
    void the_current_user_exposes_the_fleetpilot_claims() {
        login(token(Map.of("realm_access", Map.of("roles", List.of("driver")), "company_id", COMPANY.toString(),
                "driver_id", "87162d7c-e396-30cd-b37c-a6278869bd78", "garage_id", "not-a-uuid")));

        assertThat(CurrentUser.isAuthenticated()).isTrue();
        assertThat(CurrentUser.hasAnyRole(Roles.DRIVER)).isTrue();
        assertThat(CurrentUser.hasAnyRole(Roles.PLATFORM_ADMIN, Roles.CLIENT_MANAGER)).isFalse();
        assertThat(CurrentUser.companyId()).contains(COMPANY);
        assertThat(CurrentUser.driverId()).isPresent();
        assertThat(CurrentUser.garageId()).as("malformed claims are ignored").isEmpty();
    }

    @Test
    void outside_of_a_request_there_is_no_user() {
        assertThat(CurrentUser.isAuthenticated()).isFalse();
        assertThat(CurrentUser.companyId()).isEmpty();
        assertThat(CurrentUser.hasAnyRole(Roles.PLATFORM_ADMIN)).isFalse();
    }

    @Test
    void security_refusals_are_problem_details() throws Exception {
        ProblemSecurityHandler handler = new ProblemSecurityHandler();
        MockHttpServletResponse unauthorized = new MockHttpServletResponse();
        MockHttpServletResponse forbidden = new MockHttpServletResponse();

        handler.commence(new MockHttpServletRequest(), unauthorized, new BadCredentialsException("expired"));
        handler.handle(new MockHttpServletRequest(), forbidden, new AccessDeniedException("no"));

        assertThat(unauthorized.getStatus()).isEqualTo(401);
        assertThat(unauthorized.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(unauthorized.getContentType()).startsWith("application/problem+json");
        assertThat(unauthorized.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"", "\"status\":401");
        assertThat(forbidden.getStatus()).isEqualTo(403);
        assertThat(forbidden.getContentAsString()).contains("\"code\":\"ACCESS_DENIED\"", "problems/access-denied");
    }
}
