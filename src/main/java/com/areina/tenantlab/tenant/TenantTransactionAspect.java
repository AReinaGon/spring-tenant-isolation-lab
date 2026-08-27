package com.areina.tenantlab.tenant;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The spike answer: how {@code set_config('app.current_tenant', ...)} reaches the exact
 * connection Hibernate will use, after the transaction has started.
 *
 * <p>The aspect runs <em>inside</em> the transaction. {@code SecurityConfig} orders the
 * transaction advisor outermost (HIGHEST_PRECEDENCE) and this aspect innermost
 * (LOWEST_PRECEDENCE), so by the time this code executes the JPA transaction is already
 * active. Hibernate still has not acquired the JDBC connection (connections are acquired
 * lazily on the first statement), so {@code Session.doWork(...)} acquires it right now and
 * executes {@code set_config(..., true)} on it. The {@code true} makes the setting local to
 * the current transaction, so commit and rollback discard it: a pooled connection cannot
 * carry one tenant's setting into the next request.</p>
 *
 * <p>Hikari's {@code connectionInitSql}, a session-level {@code SET} and the connection
 * provider hooks were all rejected because they run outside the transaction or leak across
 * pooled connections; see {@code docs/evidence.md}.</p>
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantTransactionAspect {

    private static final String SET_TENANT_SQL = "select set_config('app.current_tenant', ?, true)";

    private final EntityManager entityManager;

    public TenantTransactionAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Around("@within(com.areina.tenantlab.tenant.TenantScoped) && "
        + "(@annotation(org.springframework.transaction.annotation.Transactional) "
        + "|| @within(org.springframework.transaction.annotation.Transactional))")
    public Object propagateTenant(ProceedingJoinPoint joinPoint) throws Throwable {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new MissingTenantContextException(
                "No tenant id bound to the context for " + joinPoint.getSignature().toShortString()
                    + "; refusing to run a tenant-scoped operation");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                "Tenant-scoped operation " + joinPoint.getSignature().toShortString()
                    + " ran outside an active transaction");
        }

        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SET_TENANT_SQL)) {
                statement.setString(1, tenantId);
                statement.execute();
            } catch (SQLException e) {
                throw new RuntimeException("Could not set app.current_tenant on the JDBC connection", e);
            }
        });

        return joinPoint.proceed();
    }
}
