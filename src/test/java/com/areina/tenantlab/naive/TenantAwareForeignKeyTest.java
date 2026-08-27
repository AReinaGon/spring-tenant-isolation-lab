package com.areina.tenantlab.naive;

import static com.areina.tenantlab.support.TestData.TENANT_A;
import static com.areina.tenantlab.support.TestData.WORKSPACE_A;
import static com.areina.tenantlab.support.TestData.WORKSPACE_B;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.areina.tenantlab.AbstractPostgresIntegrationTest;
import com.areina.tenantlab.document.DocumentService;
import com.areina.tenantlab.document.RawDocumentInsertCommand;
import com.areina.tenantlab.support.TestPrincipal;
import com.areina.tenantlab.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * The composite foreign key {@code (tenant_id, workspace_id)} prevents a document from
 * referencing another tenant's workspace even before any Row-Level Security is in play.
 * These tests run in the naive schema so the FK is the only boundary being exercised.
 */
@SpringBootTest
@ActiveProfiles("naive")
class TenantAwareForeignKeyTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DocumentService documentService;

    @Test
    void documentCannotReferenceAWorkspaceFromAnotherTenant() {
        TestPrincipal.authenticate(TENANT_A, "ana", List.of("EDITOR"), "documents:read");
        TenantContext.set(TENANT_A);
        try {
            RawDocumentInsertCommand crossTenant = new RawDocumentInsertCommand(
                UUID.randomUUID(), TENANT_A, WORKSPACE_B, "attached-to-foreign-workspace", "body", "ana");
            assertThatThrownBy(() -> documentService.insertDocumentForTenantRaw(crossTenant))
                .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            TenantContext.clear();
            TestPrincipal.clear();
        }
    }

    @Test
    void documentCanReferenceAWorkspaceInTheSameTenant() {
        TestPrincipal.authenticate(TENANT_A, "ana", List.of("EDITOR"), "documents:read");
        TenantContext.set(TENANT_A);
        try {
            RawDocumentInsertCommand own = new RawDocumentInsertCommand(
                UUID.randomUUID(), TENANT_A, WORKSPACE_A, "own-workspace", "body", "ana");
            assertThatCode(() -> documentService.insertDocumentForTenantRaw(own))
                .doesNotThrowAnyException();
        } finally {
            TenantContext.clear();
            TestPrincipal.clear();
        }
    }
}
