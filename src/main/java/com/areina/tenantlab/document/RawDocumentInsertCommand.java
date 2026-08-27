package com.areina.tenantlab.document;

import java.util.UUID;

/**
 * Input for the deliberately unsafe write path ({@code /lab/raw/documents}). The tenant id
 * is taken from the caller and written verbatim through a native query that bypasses
 * Hibernate's {@code @TenantId}. In the "secured" deployment Row-Level Security
 * {@code WITH CHECK} rejects it; in the "naive" deployment it succeeds, which is the
 * demonstration. Teaching material only.
 */
public record RawDocumentInsertCommand(
        UUID id,
        String tenantId,
        UUID workspaceId,
        String title,
        String content,
        String createdBy) {
}
