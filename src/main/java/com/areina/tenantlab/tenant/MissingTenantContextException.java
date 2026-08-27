package com.areina.tenantlab.tenant;

/**
 * Thrown when a tenant-scoped operation runs without a tenant bound to the context.
 * Fails closed: the operation is aborted instead of running across all tenants.
 */
public class MissingTenantContextException extends RuntimeException {

    public MissingTenantContextException(String message) {
        super(message);
    }
}
