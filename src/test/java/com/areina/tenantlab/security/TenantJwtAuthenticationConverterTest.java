package com.areina.tenantlab.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Narrow unit test for the claim-to-authority mapping. It does not pretend to exercise
 * cryptography: {@code JwtValidationTest} does that end-to-end with real signatures. This
 * test only pins the explicit role/scope conversion and the fail-closed missing-tenant case.
 */
class TenantJwtAuthenticationConverterTest {

    private final TenantJwtAuthenticationConverter converter = new TenantJwtAuthenticationConverter();

    @Test
    void rolesAndScopeBecomeExplicitAuthorities() {
        Jwt jwt = jwt().claim("tenant_id", "tenant-a")
            .claim("roles", List.of("EDITOR"))
            .claim("scope", "documents:read")
            .build();

        TenantAuthenticationToken token = (TenantAuthenticationToken) converter.convert(jwt);

        assertThat(token.getTenantId()).isEqualTo("tenant-a");
        assertThat(token.getSubject()).isEqualTo("ana");
        assertThat(token.getAuthorities().stream().map(GrantedAuthority::getAuthority))
            .containsExactlyInAnyOrder("ROLE_EDITOR", "SCOPE_documents:read");
    }

    @Test
    void spaceDelimitedScopesBecomeSeparateAuthorities() {
        Jwt jwt = jwt().claim("tenant_id", "tenant-a")
            .claim("scope", "openid documents:read profile")
            .build();

        TenantAuthenticationToken token = (TenantAuthenticationToken) converter.convert(jwt);

        assertThat(token.getAuthorities().stream().map(GrantedAuthority::getAuthority))
            .containsExactlyInAnyOrder("SCOPE_openid", "SCOPE_documents:read", "SCOPE_profile");
    }

    @Test
    void missingTenantClaimFailsClosed() {
        Jwt jwt = jwt().build();

        assertThatThrownBy(() -> converter.convert(jwt))
            .isInstanceOf(OAuth2AuthenticationException.class);
    }

    private Jwt.Builder jwt() {
        return Jwt.withTokenValue("not-a-real-token")
            .header("alg", "RS256")
            .subject("ana")
            .issuer("https://identity.example.test")
            .audience(List.of("document-api"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300));
    }
}
