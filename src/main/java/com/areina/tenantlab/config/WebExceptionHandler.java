package com.areina.tenantlab.config;

import com.areina.tenantlab.document.DocumentNotFoundException;
import com.areina.tenantlab.tenant.MissingTenantContextException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the lab's domain failures to HTTP semantics.
 *
 * <p>An unknown or cross-tenant document is a {@code 404}: the resource is simply not
 * visible to this principal. A missing tenant context is an identity problem, so it maps to
 * {@code 401}. A write rejected by the database (Row-Level Security {@code WITH CHECK} or a
 * tenant-aware foreign key) is a {@code 409}.</p>
 */
@RestControllerAdvice
public class WebExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    ProblemDetail handleNotFound(DocumentNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MissingTenantContextException.class)
    ProblemDetail handleMissingTenant(MissingTenantContextException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        detail.setTitle("No tenant context");
        return detail;
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail handleDataAccess(DataAccessException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The database rejected the write");
    }
}
