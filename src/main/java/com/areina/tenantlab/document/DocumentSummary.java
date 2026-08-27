package com.areina.tenantlab.document;

import java.util.UUID;

/**
 * API view of a document. The tenant id is part of the response on purpose: tests assert it
 * to prove whether a cross-tenant row was leaked.
 */
public record DocumentSummary(UUID id, String tenantId, UUID workspaceId, String title) {

    public static DocumentSummary from(Document document) {
        return new DocumentSummary(
            document.getId(), document.getTenantId(), document.getWorkspaceId(), document.getTitle());
    }

    public static DocumentSummary from(DocumentVulnerable document) {
        return new DocumentSummary(
            document.getId(), document.getTenantId(), document.getWorkspaceId(), document.getTitle());
    }
}
