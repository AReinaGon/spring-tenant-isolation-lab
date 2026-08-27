package com.areina.tenantlab.config;

import com.areina.tenantlab.tenant.TenantResolver;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Hibernate's discriminator multitenancy for the lab.
 *
 * <p>{@code @TenantId} fields plus a {@link CurrentTenantIdentifierResolver} make Hibernate
 * automatically filter every ORM operation to the current tenant. The resolver instance is
 * handed to Hibernate through a {@link HibernatePropertiesCustomizer}; it reads the
 * thread-bound {@link com.areina.tenantlab.tenant.TenantContext}, so the same value that
 * drives RLS also drives the ORM discriminator.</p>
 */
@Configuration
public class TenantPersistenceConfig {

    @Bean
    CurrentTenantIdentifierResolver<String> currentTenantIdentifierResolver() {
        return new TenantResolver();
    }

    @Bean
    HibernatePropertiesCustomizer hibernateTenantResolverCustomizer(CurrentTenantIdentifierResolver<String> resolver) {
        return properties -> properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
