package com.areina.tenantlab.document;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teaching demonstrations, isolated under {@code /lab/} and deliberately NOT part of the
 * secure-by-default surface. They exist so the tests can observe what a buggy query looks
 * like before Row-Level Security and what the ORM alone cannot contain. Do not copy the
 * unsafe paths into real code.
 */
@RestController
@RequestMapping("/lab")
public class LabDemoController {

    private final DocumentService documentService;
    private final VulnerableDocumentService vulnerableDocumentService;

    public LabDemoController(DocumentService documentService, VulnerableDocumentService vulnerableDocumentService) {
        this.documentService = documentService;
        this.vulnerableDocumentService = vulnerableDocumentService;
    }

    @GetMapping("/vulnerable/documents/{id}")
    public DocumentSummary vulnerableFindById(@PathVariable UUID id) {
        return vulnerableDocumentService.findById(id);
    }

    @GetMapping("/native/documents/{id}")
    public DocumentSummary nativeFindById(@PathVariable UUID id) {
        return documentService.getDocumentViaNativeQuery(id);
    }

    @PostMapping("/raw/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentSummary rawInsert(@RequestBody RawDocumentInsertCommand command) {
        documentService.insertDocumentForTenantRaw(command);
        return new DocumentSummary(command.id(), command.tenantId(), command.workspaceId(), command.title());
    }
}
