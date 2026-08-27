package com.areina.tenantlab.secured;

import static com.areina.tenantlab.support.TestData.DOC_A;
import static com.areina.tenantlab.support.TestData.TENANT_A;
import static com.areina.tenantlab.support.TestData.TENANT_B;
import static com.areina.tenantlab.support.TestData.WORKSPACE_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.areina.tenantlab.AbstractPostgresIntegrationTest;
import com.areina.tenantlab.document.DocumentNotFoundException;
import com.areina.tenantlab.document.DocumentService;
import com.areina.tenantlab.document.RawDocumentInsertCommand;
import com.areina.tenantlab.support.TestJwtFactory;
import com.areina.tenantlab.support.TestPrincipal;
import com.areina.tenantlab.tenant.TenantContext;
import com.areina.tenantlab.tenant.TenantProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The connection-pool properties of the design, with a pool of size 1 so that reuse of the
 * exact same physical connection is deterministic:
 *
 * <ul>
 *   <li>the tenant context never leaks between requests (the filter always clears it);</li>
 *   <li>{@code app.current_tenant} is transaction-local, so a pooled connection reused after
 *   commit or rollback does not inherit the previous tenant's setting;</li>
 *   <li>the transaction aspect re-establishes the setting at the start of every transaction.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("secured")
class SecuredPoolTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantProbe tenantProbe;

    @Autowired
    private DocumentService documentService;

    @Test
    void tenantContextIsClearedBetweenRequests() throws Exception {
        // First request as tenant-a reads its document.
        mockMvc.perform(get("/documents/{id}", DOC_A)
                .header(HttpHeaders.AUTHORIZATION, bearer(TestJwtFactory.anaEditor())))
            .andExpect(status().isOk());
        // Second request as tenant-b must NOT see it. If the tenant from request 1 had
        // leaked (filter finally), this request would run as tenant-a and return 200.
        mockMvc.perform(get("/documents/{id}", DOC_A)
                .header(HttpHeaders.AUTHORIZATION, bearer(TestJwtFactory.bobEditor())))
            .andExpect(status().isNotFound());
    }

    @Test
    void pooledConnectionReuseAfterCommitDoesNotLeakTenant() {
        String firstBackendPid = runAs(TENANT_A, () -> {
            TenantProbe.DocumentVisibilityProbe probe = tenantProbe.documentVisibility(DOC_A);
            assertThat(probe.currentTenantSetting()).isEqualTo(TENANT_A);
            assertThat(probe.visibleRows()).isEqualTo(1);
            return probe.backendPid();
        });

        runAs(TENANT_B, () -> {
            TenantProbe.DocumentVisibilityProbe probe = tenantProbe.documentVisibility(DOC_A);
            // The aspect re-established the tenant for THIS transaction on the very same
            // physical connection the previous transaction committed on.
            assertThat(probe.currentTenantSetting()).isEqualTo(TENANT_B);
            assertThat(probe.backendPid()).isEqualTo(firstBackendPid);
            // tenant-a's document is invisible to tenant-b, even on a reused connection.
            assertThat(probe.visibleRows()).isZero();
            assertThatThrownBy(() -> documentService.getDocument(DOC_A))
                .isInstanceOf(DocumentNotFoundException.class);
        });
    }

    @Test
    void pooledConnectionReuseAfterRollbackDoesNotLeakTenant() {
        String firstBackendPid = runAs(TENANT_A, () -> {
            String pid = tenantProbe.documentVisibility(DOC_A).backendPid();
            // Force a rollback: a native write for another tenant is rejected by WITH CHECK.
            assertThatThrownBy(() -> documentService.insertDocumentForTenantRaw(
                new RawDocumentInsertCommand(UUID.randomUUID(), TENANT_B, WORKSPACE_B,
                    "pivot", "x", "ana")))
                .isInstanceOf(DataAccessException.class);
            return pid;
        });

        runAs(TENANT_B, () -> {
            TenantProbe.DocumentVisibilityProbe probe = tenantProbe.documentVisibility(DOC_A);
            assertThat(probe.currentTenantSetting()).isEqualTo(TENANT_B);
            assertThat(probe.backendPid()).isEqualTo(firstBackendPid);
            assertThat(probe.visibleRows()).isZero();
        });
    }

    private void runAs(String tenantId, Runnable action) {
        TenantContext.set(tenantId);
        TestPrincipal.authenticate(tenantId, "ana", List.of("EDITOR"), "documents:read");
        try {
            action.run();
        } finally {
            TenantContext.clear();
            TestPrincipal.clear();
        }
    }

    private <T> T runAs(String tenantId, java.util.function.Supplier<T> action) {
        TenantContext.set(tenantId);
        TestPrincipal.authenticate(tenantId, "ana", List.of("EDITOR"), "documents:read");
        try {
            return action.get();
        } finally {
            TenantContext.clear();
            TestPrincipal.clear();
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
