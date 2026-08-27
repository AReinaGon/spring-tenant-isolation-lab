package com.areina.tenantlab.tenant;

/**
 * Thread-bound current tenant id.
 *
 * <p>Spring MVC is request-per-thread, so a {@link ThreadLocal} with a clearly scoped
 * lifecycle is enough for this lab. The value is set by {@code TenantContextFilter} from a
 * validated identity and always cleared in {@code finally}; a test verifies that a request
 * cannot contaminate the next one.</p>
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String get() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
