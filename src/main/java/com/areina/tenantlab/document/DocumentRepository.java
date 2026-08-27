package com.areina.tenantlab.document;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Tenant-aware repository. Every ORM operation here is filtered by Hibernate's
 * {@code @TenantId} to the current tenant.
 */
public interface DocumentRepository extends JpaRepository<Document, UUID> {
}
