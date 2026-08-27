package com.areina.tenantlab.security;

import java.io.IOException;

import com.areina.tenantlab.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds the request's tenant to the {@link TenantContext} for the duration of the request and
 * always clears it in {@code finally}. The tenant comes only from the already-authenticated
 * {@link TenantAuthenticationToken}; any other case clears the context so a tenant-less
 * request fails closed at the transaction layer instead of reading every tenant.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof TenantAuthenticationToken tenantToken) {
                TenantContext.set(tenantToken.getTenantId());
            } else {
                TenantContext.clear();
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
