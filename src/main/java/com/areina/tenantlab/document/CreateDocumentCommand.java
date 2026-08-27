package com.areina.tenantlab.document;

import java.util.UUID;

/**
 * Input for the secure write path. The tenant is NOT part of the command: Hibernate fills
 * {@code tenant_id} from the session's tenant.
 */
public record CreateDocumentCommand(UUID workspaceId, String title, String content) {
}
