package com.areina.tenantlab.naive;

import static com.areina.tenantlab.support.TestData.DOC_A;
import static com.areina.tenantlab.support.TestData.DOC_B;
import static com.areina.tenantlab.support.TestData.TENANT_A;
import static com.areina.tenantlab.support.TestData.TENANT_B;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.areina.tenantlab.AbstractPostgresIntegrationTest;
import com.areina.tenantlab.support.TestJwtFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The "naive" deployment: the exact same schema and queries, but Row-Level Security is not
 * enabled. These tests are the first three scenes of the story: the perimeter and RBAC do
 * not stop a buggy query, Hibernate's {@code @TenantId} reduces the risk, and a native query
 * escapes the ORM filter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("naive")
class NaiveLeakTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void perimeterValidationAndRbacDoNotContainVulnerableFindById() throws Exception {
        // Scene 1: the token is valid (correct signature, iss, aud, exp), the role is EDITOR,
        // and yet findById on an entity without @TenantId returns tenant-b's document.
        mockMvc.perform(get("/lab/vulnerable/documents/{id}", DOC_B)
                .header(HttpHeaders.AUTHORIZATION, bearer(TestJwtFactory.anaEditor())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(DOC_B.toString()))
            .andExpect(jsonPath("$.tenantId").value(TENANT_B));
    }

    @Test
    void ormQueryIsFilteredToTheCurrentTenant() throws Exception {
        // Scene 2: with @TenantId in place, the ORM path no longer returns the other tenant's
        // document even without RLS.
        mockMvc.perform(get("/documents/{id}", DOC_B)
                .header(HttpHeaders.AUTHORIZATION, bearer(TestJwtFactory.anaEditor())))
            .andExpect(status().isNotFound());
    }

    @Test
    void nativeQueryBypassesHibernateTenantFilter() throws Exception {
        // Scene 3: the ORM filter is a limit. The same read intent through native SQL leaks
        // again in the naive schema, because Hibernate does not filter native queries.
        mockMvc.perform(get("/lab/native/documents/{id}", DOC_B)
                .header(HttpHeaders.AUTHORIZATION, bearer(TestJwtFactory.anaEditor())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantId").value(TENANT_B));
    }

    @Test
    void editorReadsOwnDocument() throws Exception {
        mockMvc.perform(get("/documents/{id}", DOC_A)
                .header(HttpHeaders.AUTHORIZATION, bearer(TestJwtFactory.anaEditor())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantId").value(TENANT_A));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
