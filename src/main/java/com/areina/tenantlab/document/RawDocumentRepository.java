package com.areina.tenantlab.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;

/**
 * Native SQL on the {@code documents} table. Hibernate does not add the tenant predicate to
 * native queries, so these methods are exactly the "query that forgets the tenant id". They
 * are the vehicle for demonstrating both the ORM limit and the Row-Level Security backstop.
 */
@Repository
public class RawDocumentRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<DocumentSummary> findByIdUnsafe(UUID id) {
        List<Object[]> rows = entityManager.unwrap(Session.class).createNativeQuery(
                "select d.id, d.tenant_id, d.workspace_id, d.title "
                    + "from {h-schema}documents d where d.id = :id", Object[].class)
            .setParameter("id", id)
            .getResultList();
        return rows.stream().findFirst().map(this::toSummary);
    }

    /**
     * Lab-only unsafe write: trusts a caller-supplied tenant id and inserts it verbatim.
     * Under RLS the {@code WITH CHECK} policy rejects it; in the naive schema it succeeds.
     */
    public int insertForTenantRaw(RawDocumentInsertCommand command) {
        return entityManager.createNativeQuery(
                "insert into {h-schema}documents (id, tenant_id, workspace_id, title, content, created_by) "
                    + "values (:id, :tenantId, :workspaceId, :title, :content, :createdBy)")
            .setParameter("id", command.id())
            .setParameter("tenantId", command.tenantId())
            .setParameter("workspaceId", command.workspaceId())
            .setParameter("title", command.title())
            .setParameter("content", command.content())
            .setParameter("createdBy", command.createdBy())
            .executeUpdate();
    }

    /**
     * Lab-only unsafe update: moves a document to another tenant. Under RLS the
     * {@code WITH CHECK} policy rejects the write with a database error.
     */
    public int moveToTenantRaw(UUID id, String tenantId) {
        return entityManager.createNativeQuery(
                "update {h-schema}documents set tenant_id = :tenantId where id = :id")
            .setParameter("tenantId", tenantId)
            .setParameter("id", id)
            .executeUpdate();
    }

    private DocumentSummary toSummary(Object[] row) {
        return new DocumentSummary(
            (UUID) row[0],
            (String) row[1],
            (UUID) row[2],
            (String) row[3]);
    }
}
