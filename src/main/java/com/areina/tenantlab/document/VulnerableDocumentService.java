package com.areina.tenantlab.document;

import java.util.UUID;

import com.areina.tenantlab.tenant.TenantScoped;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The deliberately vulnerable path: identical RBAC requirements, but {@code findById} on the
 * entity without {@code @TenantId}. Perimeter validation and role checks alone do not stop
 * it; only the database does, when RLS is enabled. Teaching material only.
 */
@Service
@TenantScoped
@Transactional(readOnly = true)
public class VulnerableDocumentService {

    private final VulnerableDocumentRepository vulnerableDocumentRepository;

    public VulnerableDocumentService(VulnerableDocumentRepository vulnerableDocumentRepository) {
        this.vulnerableDocumentRepository = vulnerableDocumentRepository;
    }

    @PreAuthorize("hasRole('EDITOR') and hasAuthority('SCOPE_documents:read')")
    public DocumentSummary findById(UUID id) {
        return vulnerableDocumentRepository.findById(id)
            .map(DocumentSummary::from)
            .orElseThrow(() -> new DocumentNotFoundException(id));
    }
}
