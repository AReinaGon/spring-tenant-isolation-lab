package com.areina.tenantlab.document;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The buggy version of the same {@code documents} table: identical mapping minus
 * {@code @TenantId}. This is exactly the "scaffolding wrote findById without the tenant
 * column" variant. {@code findById} on this entity carries no tenant predicate, so the
 * database is the only layer that can contain it (Row-Level Security).
 *
 * <p>Teaching material only, used by the {@code /lab/vulnerable/...} demonstration. It is
 * never part of the secure-by-default surface.</p>
 */
@Entity
@Table(name = "documents")
public class DocumentVulnerable {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    protected DocumentVulnerable() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
