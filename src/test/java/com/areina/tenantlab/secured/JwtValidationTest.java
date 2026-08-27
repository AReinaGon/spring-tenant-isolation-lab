package com.areina.tenantlab.secured;

import static com.areina.tenantlab.support.TestData.AUDIENCE;
import static com.areina.tenantlab.support.TestData.DOC_A;
import static com.areina.tenantlab.support.TestData.ISSUER;
import static com.areina.tenantlab.support.TestData.TENANT_A;
import static com.areina.tenantlab.support.TestData.TENANT_B;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
 * Real-cryptography validation of the Bearer token inside {@code Document API}. Unlike
 * {@code SecurityMockMvcRequestPostProcessors.jwt()}, these requests send an actually signed
 * JWT through the whole chain: the decoder verifies the RSA signature, the expiry window,
 * the issuer and the audience before the request is authenticated.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("secured")
class JwtValidationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validTokenAllowsReadingOwnDocument() throws Exception {
        mockMvc.perform(get("/documents/{id}", DOC_A)
                .header(HttpHeaders.AUTHORIZATION, bearer(TestJwtFactory.anaEditor())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantId").value(TENANT_A));
    }

    @Test
    void tamperedSignatureIsRejected() throws Exception {
        mockMvc.perform(get("/documents/{id}", DOC_A)
                .header(HttpHeaders.AUTHORIZATION,
                    bearer(TestJwtFactory.builder().signWithDifferentKey().build())))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceIsRejected() throws Exception {
        mockMvc.perform(get("/documents/{id}", DOC_A)
                .header(HttpHeaders.AUTHORIZATION,
                    bearer(TestJwtFactory.builder().audience("checkout-api").build())))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongIssuerIsRejected() throws Exception {
        mockMvc.perform(get("/documents/{id}", DOC_A)
                .header(HttpHeaders.AUTHORIZATION,
                    bearer(TestJwtFactory.builder().issuer("https://evil.example.test").build())))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        mockMvc.perform(get("/documents/{id}", DOC_A)
                .header(HttpHeaders.AUTHORIZATION, bearer(TestJwtFactory.builder()
                    .expiresAt(Instant.now().minus(Duration.ofMinutes(5))).build())))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTenantClaimIsRejectedFailClosed() throws Exception {
        mockMvc.perform(get("/documents/{id}", DOC_A)
                .header(HttpHeaders.AUTHORIZATION, bearer(TestJwtFactory.builder().withoutTenantClaim().build())))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/documents/{id}", DOC_A)
                .header(HttpHeaders.AUTHORIZATION,
                    bearer(TestJwtFactory.builder().roles(List.of("VIEWER")).build())))
            .andExpect(status().isForbidden());
    }

    @Test
    void editorWithoutScopeIsForbidden() throws Exception {
        mockMvc.perform(get("/documents/{id}", DOC_A)
                .header(HttpHeaders.AUTHORIZATION,
                    bearer(TestJwtFactory.builder().scope(null).build())))
            .andExpect(status().isForbidden());
    }

    @Test
    void otherTenantDocumentIsNotVisible() throws Exception {
        mockMvc.perform(get("/documents/{id}", DOC_A)
                .header(HttpHeaders.AUTHORIZATION, bearer(TestJwtFactory.bobEditor())))
            .andExpect(status().isNotFound());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
