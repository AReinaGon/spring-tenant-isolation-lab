package com.areina.tenantlab.document;

import java.util.UUID;

import com.areina.tenantlab.tenant.TenantScoped;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The secure-by-default business path. Method security enforces the RBAC/ABAC decision, and
 * the {@code @TenantId} mapping (for ORM) plus Row-Level Security (for everything else) are
 * the isolation backstops.
 */
@Service
@TenantScoped
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final RawDocumentRepository rawDocumentRepository;

    public DocumentService(DocumentRepository documentRepository, RawDocumentRepository rawDocumentRepository) {
        this.documentRepository = documentRepository;
        this.rawDocumentRepository = rawDocumentRepository;
    }

    @PreAuthorize("hasRole('EDITOR') and hasAuthority('SCOPE_documents:read')")
    public DocumentSummary getDocument(UUID id) {
        return documentRepository.findById(id)
            .map(DocumentSummary::from)
            .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    /**
     * Same read intent as {@link #getDocument(UUID)} but through native SQL, which Hibernate
     * does not filter by tenant. Used to demonstrate the ORM limit and the RLS backstop.
     */
    @PreAuthorize("hasRole('EDITOR') and hasAuthority('SCOPE_documents:read')")
    public DocumentSummary getDocumentViaNativeQuery(UUID id) {
        return rawDocumentRepository.findByIdUnsafe(id)
            .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Transactional
    @PreAuthorize("hasRole('EDITOR')")
    public DocumentSummary createDocument(CreateDocumentCommand command) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setWorkspaceId(command.workspaceId());
        document.setTitle(command.title());
        document.setContent(command.content());
        document.setCreatedBy(currentSubject());
        // saveAndFlush makes Hibernate populate the @TenantId field before we build the view.
        return DocumentSummary.from(documentRepository.saveAndFlush(document));
    }

    /**
     * Lab-only unsafe write: delegates to a native insert that trusts a caller-supplied
     * tenant id. Row-Level Security {@code WITH CHECK} is the layer that must contain it.
     */
    @Transactional
    @PreAuthorize("hasRole('EDITOR')")
    public void insertDocumentForTenantRaw(RawDocumentInsertCommand command) {
        rawDocumentRepository.insertForTenantRaw(command);
    }

    /**
     * Lab-only unsafe update: moves a document to a caller-supplied tenant id via native SQL.
     * Under RLS the {@code WITH CHECK} policy rejects the write with a database error.
     *
     * @return number of rows actually updated
     */
    @Transactional
    @PreAuthorize("hasRole('EDITOR')")
    public int moveDocumentToTenantRaw(UUID id, String tenantId) {
        return rawDocumentRepository.moveToTenantRaw(id, tenantId);
    }

    private String currentSubject() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
