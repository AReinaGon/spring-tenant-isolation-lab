package com.areina.tenantlab.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Hibernate's resolver for the current tenant id. Stateless on purpose: Hibernate may
 * instantiate it itself, so it only reads the static {@link TenantContext}.
 *
 * <p>When no tenant is bound it returns a sentinel that matches no row, keeping the ORM
 * fail-closed; the transaction aspect throws a dedicated exception before any query when the
 * context is missing, so the sentinel is a backstop, not a path.</p>
 */
public class TenantResolver implements CurrentTenantIdentifierResolver<String> {

    static final String NO_TENANT_SENTINEL = "no-tenant";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantId = TenantContext.get();
        return tenantId != null ? tenantId : NO_TENANT_SENTINEL;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(String tenantId) {
        return false;
    }
}
