package com.areina.tenantlab.document;

import java.util.UUID;

/**
 * A document is not visible to the current principal (missing or cross-tenant), so it maps
 * to {@code 404}.
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(UUID id) {
        super("Document " + id + " not found");
    }
}
