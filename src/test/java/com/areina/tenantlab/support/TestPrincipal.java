package com.areina.tenantlab.support;

import java.util.ArrayList;
import java.util.List;

import com.areina.tenantlab.security.TenantAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Sets a pre-authenticated principal for service-level tests. These tests do not claim to
 * exercise cryptography: the HTTP tests in {@code JwtValidationTest} do that with real
 * tokens. This helper only lets a service-level test run as a specific tenant + role.
 */
public final class TestPrincipal {

    private TestPrincipal() {
    }

    public static void authenticate(String tenantId, String subject, List<String> roles, String scope) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        if (scope != null && !scope.isBlank()) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }
        SecurityContextHolder.getContext()
            .setAuthentication(new TenantAuthenticationToken(tenantId, subject, authorities));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
