package com.areina.tenantlab.security;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * An authenticated principal for the lab, carrying the tenant id that was read from a
 * validated JWT claim. The tenant id is never supplied by the client: it comes exclusively
 * from claims that survived signature, expiry, issuer and audience validation.
 */
public class TenantAuthenticationToken extends AbstractAuthenticationToken {

    private final String tenantId;
    private final String subject;

    public TenantAuthenticationToken(String tenantId, String subject,
                                     Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.tenantId = tenantId;
        this.subject = subject;
        setAuthenticated(true);
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getSubject() {
        return subject;
    }

    @Override
    public Object getPrincipal() {
        return subject;
    }

    @Override
    public Object getCredentials() {
        return "";
    }
}
