package com.areina.tenantlab.tenant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a business component as tenant-scoped: every {@code @Transactional} operation inside
 * it runs with {@code app.current_tenant} set on the same JDBC connection Hibernate will
 * query. See {@link TenantTransactionAspect}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantScoped {
}
