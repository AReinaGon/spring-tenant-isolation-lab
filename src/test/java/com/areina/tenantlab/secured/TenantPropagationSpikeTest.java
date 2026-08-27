package com.areina.tenantlab.secured;

import static com.areina.tenantlab.support.TestData.DOC_A;
import static com.areina.tenantlab.support.TestData.TENANT_A;
import static com.areina.tenantlab.support.TestData.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.areina.tenantlab.AbstractPostgresIntegrationTest;
import com.areina.tenantlab.support.TestPrincipal;
import com.areina.tenantlab.tenant.MissingTenantContextException;
import com.areina.tenantlab.tenant.TenantContext;
import com.areina.tenantlab.tenant.TenantProbe;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The mandatory spike, captured as executable evidence: how {@code set_config} reaches the
 * exact connection Hibernate will use, after the transaction starts.
 *
 * <p>Application path: {@link TenantTransactionAspect} runs inside the transaction and calls
 * {@code Session.doWork(...)}, so the setting is established before the first query, on the
 * same physical connection. Database path (raw {@code TransactionTemplate}, no aspect): the
 * {@code true} in {@code set_config(..., true)} makes the setting local to the transaction,
 * so commit and rollback discard it and a pooled connection cannot inherit it.</p>
 *
 * <p>Every assertion below is id-based (document DOC_A), never a global count, so no test
 * depends on what another test wrote.</p>
 */
@SpringBootTest
@ActiveProfiles("secured")
class TenantPropagationSpikeTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TenantProbe tenantProbe;

    @PersistenceContext
    private EntityManager entityManager;

    private TransactionTemplate transactionTemplate;

    @Autowired
    void setTransactionTemplate(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void applicationPathSetsTheSettingInsideTheTransactionOnTheSameConnection() {
        TenantContext.set(TENANT_A);
        try {
            // The aspect sets the tenant before the first query; the visibility probe then
            // reads the setting AND the RLS-filtered row count on that same connection, so a
            // visible row is proof they shared the connection.
            TenantProbe.DocumentVisibilityProbe probe = tenantProbe.documentVisibility(DOC_A);
            assertThat(probe.currentTenantSetting()).isEqualTo(TENANT_A);
            assertThat(probe.visibleRows()).isEqualTo(1);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void transactionLocalSettingDisappearsAfterCommitAndRollback() {
        // Transaction 1 (commits): set app.current_tenant locally and verify RLS sees the row.
        RawSnapshot committed = rawSetTenantAndSnapshot(TENANT_A, false);
        assertThat(committed.setting()).isEqualTo(TENANT_A);
        assertThat(committed.visibleRows()).isEqualTo(1);

        // Transaction 2 (no set): same physical connection (pool of 1), setting is gone.
        // PostgreSQL reverts a custom GUC to '' after the transaction that set it locally;
        // on a never-touched connection it is NULL. Either way RLS matches no row.
        RawSnapshot afterCommit = rawSnapshot();
        assertThat(afterCommit.setting()).isNullOrEmpty();
        assertThat(afterCommit.visibleRows()).isZero();
        assertThat(afterCommit.backendPid()).isEqualTo(committed.backendPid());

        // Transaction 3 (rolls back): set again, then roll back.
        RawSnapshot rolledBack = rawSetTenantAndSnapshot(TENANT_A, true);
        assertThat(rolledBack.setting()).isEqualTo(TENANT_A);
        assertThat(rolledBack.visibleRows()).isEqualTo(1);

        // Transaction 4 (no set): the rollback also discarded the setting.
        RawSnapshot afterRollback = rawSnapshot();
        assertThat(afterRollback.setting()).isNullOrEmpty();
        assertThat(afterRollback.visibleRows()).isZero();
        assertThat(afterRollback.backendPid()).isEqualTo(rolledBack.backendPid());
    }

    @Test
    void pooledConnectionReuseDoesNotInheritThePreviousTenant() {
        // The DB-level counterpart of SecuredPoolTest: the same backend serves consecutive
        // transactions, and a transaction for tenant-b never sees a stale tenant-a value.
        runAs(TENANT_A, () -> {
            assertThat(tenantProbe.documentVisibility(DOC_A).currentTenantSetting())
                .isEqualTo(TENANT_A);
            return null;
        });

        runAs(TENANT_B, () -> {
            TenantProbe.DocumentVisibilityProbe probe = tenantProbe.documentVisibility(DOC_A);
            assertThat(probe.currentTenantSetting()).isEqualTo(TENANT_B);
            assertThat(probe.visibleRows()).isZero();
            return null;
        });
    }

    @Test
    void operationWithoutTenantFailsClosed() {
        TestPrincipal.authenticate(TENANT_A, "ana", List.of("EDITOR"), "documents:read");
        TenantContext.clear();
        try {
            // The application layer refuses to run a tenant-scoped operation with no tenant,
            // instead of accidentally running across every tenant.
            assertThatThrownBy(() -> tenantProbe.documentVisibility(DOC_A))
                .isInstanceOf(MissingTenantContextException.class);
        } finally {
            TestPrincipal.clear();
        }
    }

    private void runAs(String tenantId, java.util.function.Supplier<Object> action) {
        TestPrincipal.authenticate(tenantId, "ana", List.of("EDITOR"), "documents:read");
        TenantContext.set(tenantId);
        try {
            action.get();
        } finally {
            TenantContext.clear();
            TestPrincipal.clear();
        }
    }

    private RawSnapshot rawSetTenantAndSnapshot(String tenantId, boolean rollback) {
        return transactionTemplate.execute(status -> {
            Session session = entityManager.unwrap(Session.class);
            return session.doReturningWork(connection -> {
                try (PreparedStatement setConfig = connection.prepareStatement(
                        "select set_config('app.current_tenant', ?, true)")) {
                    setConfig.setString(1, tenantId);
                    setConfig.execute();
                }
                RawSnapshot snapshot = readSnapshot(connection);
                if (rollback) {
                    status.setRollbackOnly();
                }
                return snapshot;
            });
        });
    }

    private RawSnapshot rawSnapshot() {
        return transactionTemplate.execute(status ->
            entityManager.unwrap(Session.class).doReturningWork(this::readSnapshot));
    }

    private RawSnapshot readSnapshot(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                 "select current_setting('app.current_tenant', true), pg_backend_pid(), "
                     + "(select count(*) from documents where id = '" + DOC_A + "')")) {
            rs.next();
            return new RawSnapshot(rs.getString(1), rs.getString(2), rs.getLong(3));
        }
    }

    private record RawSnapshot(String setting, String backendPid, long visibleRows) {
    }
}
