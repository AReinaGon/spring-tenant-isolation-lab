-- Row-Level Security for the secured deployment. Applied only by the "secured" profile
-- (spring.flyway.locations includes db/migration/secured).
--
-- The policy reads a transaction-local setting that the application sets on the same
-- JDBC connection at the start of every transaction:
--
--     select set_config('app.current_tenant', :tenantId, true)
--
-- When the setting is missing, current_setting(name, true) returns NULL and `tenant_id = NULL`
-- matches no row, so the policy fails closed: a connection without a tenant sees nothing.

ALTER TABLE workspaces ENABLE ROW LEVEL SECURITY;
ALTER TABLE workspaces FORCE ROW LEVEL SECURITY;

ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents FORCE ROW LEVEL SECURITY;

-- FORCE ROW LEVEL SECURITY also applies the policy to the table owner (migrator), which is
-- never used at runtime. app_user is a plain LOGIN role without BYPASSRLS, so the policy
-- always applies to the application.

CREATE POLICY workspaces_tenant_isolation ON workspaces
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE POLICY documents_tenant_isolation ON documents
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
