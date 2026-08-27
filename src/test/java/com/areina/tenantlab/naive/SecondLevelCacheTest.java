package com.areina.tenantlab.naive;

import static com.areina.tenantlab.support.TestData.DOC_A;
import static com.areina.tenantlab.support.TestData.TENANT_A;
import static com.areina.tenantlab.support.TestData.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import com.areina.tenantlab.AbstractPostgresIntegrationTest;
import com.areina.tenantlab.document.DocumentRepository;
import com.areina.tenantlab.tenant.TenantContext;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Second-level cache interaction with {@code @TenantId}. Runs in the naive schema so
 * Row-Level Security is not doing the isolation: the only thing stopping tenant-b from
 * seeing tenant-a's cached entity is the tenant-aware cache key.
 *
 * <p>First tenant-a load primes the cache; a second tenant-a load must be a cache hit
 * (proven via Hibernate statistics); a tenant-b load of the same id must come up empty. If
 * Hibernate did not encode the tenant in the cache key, the tenant-b load would return the
 * cached tenant-a entity and this test would fail.</p>
 */
@SpringBootTest
@ActiveProfiles("naive")
class SecondLevelCacheTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private SessionFactory sessionFactory;

    @Test
    void secondLevelCacheDoesNotLeakAcrossTenants() {
        // Prime the second-level cache for tenant-a.
        runAs(TENANT_A, () -> {
            assertThat(documentRepository.findById(DOC_A)).isPresent();
            return null;
        });

        // A second tenant-a load must hit the second-level cache, not the database.
        long hitsBefore = sessionFactory.getStatistics().getSecondLevelCacheHitCount();
        runAs(TENANT_A, () -> {
            assertThat(documentRepository.findById(DOC_A)).isPresent();
            return null;
        });
        long hitsAfter = sessionFactory.getStatistics().getSecondLevelCacheHitCount();
        assertThat(hitsAfter).isGreaterThan(hitsBefore);

        // tenant-b must NOT receive tenant-a's cached entity.
        runAs(TENANT_B, () -> {
            assertThat(documentRepository.findById(DOC_A)).isEmpty();
            return null;
        });
    }

    private void runAs(String tenantId, Supplier<Object> action) {
        TenantContext.set(tenantId);
        try {
            action.get();
        } finally {
            TenantContext.clear();
        }
    }
}
