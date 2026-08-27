package com.areina.tenantlab.tenant;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lab observability: reads the transaction-local {@code app.current_tenant} and the backend
 * process id on the exact connection Hibernate is using. Used by the spike tests to prove
 * the setting lives on the same physical connection and disappears after commit/rollback.
 */
@Component
@TenantScoped
public class TenantProbe {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public TenantDbState currentTenantState() {
        return entityManager.unwrap(Session.class).doReturningWork(connection -> {
            try (var statement = connection.createStatement();
                 var resultSet = statement.executeQuery(
                     "select current_setting('app.current_tenant', true), pg_backend_pid()")) {
                resultSet.next();
                return new TenantDbState(resultSet.getString(1), resultSet.getString(2));
            }
        });
    }

    /**
     * One statement, on one connection, inside one transaction: reads the tenant setting,
     * the backend pid and how many rows of the given document Row-Level Security lets this
     * connection see. Because the row count goes through RLS on the very same connection,
     * it proves {@code set_config} and the query shared that connection.
     */
    @Transactional(readOnly = true)
    public DocumentVisibilityProbe documentVisibility(UUID documentId) {
        return entityManager.unwrap(Session.class).doReturningWork(connection -> {
            try (var statement = connection.prepareStatement(
                    "select current_setting('app.current_tenant', true), pg_backend_pid(), "
                        + "(select count(*) from documents where id = ?)")) {
                statement.setObject(1, documentId);
                try (var resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return new DocumentVisibilityProbe(
                        resultSet.getString(1), resultSet.getString(2), resultSet.getLong(3));
                }
            }
        });
    }

    /**
     * @param currentTenantSetting value of {@code app.current_tenant}, or {@code null} when
     *                             no value is set on this transaction
     * @param backendPid          PostgreSQL backend pid serving the connection
     */
    public record TenantDbState(String currentTenantSetting, String backendPid) {
    }

    /**
     * @param currentTenantSetting value of {@code app.current_tenant}, or {@code null}
     * @param backendPid          PostgreSQL backend pid serving the connection
     * @param visibleRows         rows of {@code documentId} visible through RLS on this connection
     */
    public record DocumentVisibilityProbe(String currentTenantSetting, String backendPid, long visibleRows) {
    }
}
