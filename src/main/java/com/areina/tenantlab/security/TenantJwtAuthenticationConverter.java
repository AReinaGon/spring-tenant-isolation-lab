package com.areina.tenantlab.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Converts a validated JWT into a {@link TenantAuthenticationToken}.
 *
 * <p>Role and scope claims become explicit authorities ({@code ROLE_EDITOR},
 * {@code SCOPE_documents:read}). The {@code tenant_id} claim is required: a token without it
 * is rejected here (fail-closed) instead of silently running without a tenant.</p>
 */
public class TenantJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String TENANT_CLAIM = "tenant_id";
    public static final String ROLES_CLAIM = "roles";
    public static final String SCOPE_CLAIM = "scope";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String tenantId = jwt.getClaimAsString(TENANT_CLAIM);
        if (tenantId == null || tenantId.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                "invalid_token", "Missing required claim: tenant_id", null));
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles != null) {
            for (String role : roles) {
                if (role != null && !role.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
        }
        String scope = jwt.getClaimAsString(SCOPE_CLAIM);
        if (scope != null && !scope.isBlank()) {
            for (String value : scope.trim().split("\\s+")) {
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + value));
            }
        }

        return new TenantAuthenticationToken(tenantId, jwt.getSubject(), authorities);
    }
}
