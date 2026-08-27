package com.areina.tenantlab.document;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The buggy repository: {@code findById} on an entity without {@code @TenantId}, so no
 * tenant predicate is added. Teaching material only.
 */
public interface VulnerableDocumentRepository extends JpaRepository<DocumentVulnerable, UUID> {
}
