package com.areina.tenantlab.document;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.TenantId;

/**
 * The tenant-aware document mapping. {@code @TenantId} makes Hibernate automatically filter
 * every ORM operation to the session's tenant and auto-populate the field on persist; the
 * application cannot write it directly.
 *
 * <p>The entity is second-level-cacheable (READ_WRITE). Hibernate encodes the tenant id in
 * the cache key, so a load for one tenant never returns another tenant's cached entity;
 * {@code SecondLevelCacheTest} proves it.</p>
 */
@Entity
@Table(name = "documents")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Document {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @TenantId
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

    protected Document() {
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
