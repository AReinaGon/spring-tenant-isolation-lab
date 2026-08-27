package com.areina.tenantlab.secured;

import static com.areina.tenantlab.support.TestData.DOC_A;
import static com.areina.tenantlab.support.TestData.DOC_B;
import static com.areina.tenantlab.support.TestData.TENANT_A;
import static com.areina.tenantlab.support.TestData.TENANT_B;
import static com.areina.tenantlab.support.TestData.WORKSPACE_A;
import static com.areina.tenantlab.support.TestData.WORKSPACE_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import com.areina.tenantlab.AbstractPostgresIntegrationTest;
import com.areina.tenantlab.document.DocumentService;
import com.areina.tenantlab.support.TestJwtFactory;
import com.areina.tenantlab.support.TestPrincipal;
import com.areina.tenantlab.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The "secured" deployment: same schema and queries, Row-Level Security enabled and forced,
 * the application running as a role without bypass privileges. These tests are scenes four
 * and five: the database contains the queries that escaped the ORM, and the write path plus
 * the connection pool are contained too.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("secured")
class SecuredRlsTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource appDataSource;

    @Test
    void rlsContainsVulnerableFindById() throws Exception {
        // The same buggy findById from scene 1 is now contained by the database.
        mockMvc.perform(get("/lab/vulnerable/documents/{id}", DOC_B)
                .header(HttpHeaders.AUTHORIZATION, bearer(TestJwtFactory.anaEditor())))
            .andExpect(status().isNotFound());
    }

    @Test
    void rlsContainsNativeQuery() throws Exception {
        // The same native query from scene 3, with RLS, returns nothing.
        mockMvc.perform(get("/lab/native/documents/{id}", DOC_B)
                .header(HttpHeaders.AUTHORIZATION, bearer(TestJwtFactory.anaEditor())))
            .andExpect(status().isNotFound());
    }

    @Test
    void secureWritePathPersistsDocumentInOwnTenant() throws Exception {
        // The secure POST never sees a tenant: Hibernate fills tenant_id from the context.
        String body = "{\"workspaceId\":\"" + WORKSPACE_A
            + "\",\"title\":\"Q4 plan\",\"content\":\"fresh planning notes\"}";
        mockMvc.perform(post("/documents")
                .header(HttpHeaders.AUTHORIZATION, bearer(TestJwtFactory.anaEditor()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tenantId").value(TENANT_A))
            .andExpect(jsonPath("$.workspaceId").value(WORKSPACE_A.toString()));
    }

    @Test
    void rlsWithCheckRejectsCrossTenantInsert() throws Exception {
        // A native write that trusts a caller-supplied tenant id: WITH CHECK rejects it.
        String body = "{\"id\":\"" + UUID.randomUUID()
            + "\",\"tenantId\":\"" + TENANT_B
            + "\",\"workspaceId\":\"" + WORKSPACE_B
            + "\",\"title\":\"pivot\",\"content\":\"x\",\"createdBy\":\"ana\"}";
        mockMvc.perform(post("/lab/raw/documents")
                .header(HttpHeaders.AUTHORIZATION, bearer(TestJwtFactory.anaEditor()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void updateMovingDocumentToAnotherTenantIsRejectedByWithCheck() {
        TestPrincipal.authenticate(TENANT_A, "ana", List.of("EDITOR"), "documents:read");
        TenantContext.set(TENANT_A);
        try {
            // Moving a document out of its tenant: the old row passes USING, but the new row
            // fails WITH CHECK, and PostgreSQL raises "new row violates row-level security".
            assertThatThrownBy(() -> documentService.moveDocumentToTenantRaw(DOC_A, TENANT_B))
                .isInstanceOf(DataAccessException.class);
            // A no-op move inside the same tenant still succeeds.
            assertThat(documentService.moveDocumentToTenantRaw(DOC_A, TENANT_A)).isEqualTo(1);
        } finally {
            TenantContext.clear();
            TestPrincipal.clear();
        }
    }

    @Test
    void rlsWithNoTenantSettingFailsClosed() {
        // The app role, with no app.current_tenant set, sees nothing: NULL matches no row.
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from documents where id = '" + DOC_A + "'", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void appUserHasNoRlsBypassPrivileges() throws Exception {
        try (Connection connection = appDataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                 "select current_user, r.rolsuper, r.rolbypassrls, "
                     + "(select count(*) from pg_tables t "
                     + " where t.schemaname = current_schema() and t.tablename = 'documents' "
                     + "   and t.tableowner = r.rolname) "
                     + "from pg_roles r where r.rolname = current_user")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("app_user");
            assertThat(rs.getBoolean(2)).isFalse();  // not superuser
            assertThat(rs.getBoolean(3)).isFalse();  // no BYPASSRLS
            assertThat(rs.getInt(4)).isZero();       // not the table owner
        }
    }

    @Test
    void superuserSeesAllRowsDespiteRls() throws Exception {
        // The honest caveat: a superuser (or any BYPASSRLS role) is not subject to the
        // policy. This is why the application must run as a restricted role.
        try (Connection connection = superuserConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                 "select count(*) from secured.documents where id = '" + DOC_A + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
