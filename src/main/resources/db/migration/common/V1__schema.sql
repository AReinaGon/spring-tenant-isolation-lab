-- Shared schema for both lab deployments (naive and secured).
-- Flyway applies this file inside the profile schema (spring.flyway.schemas),
-- so unqualified table names land in the right schema. The profile schema name is
-- available as the Flyway placeholder ${appSchema}.
--
-- This file deliberately creates the tables WITHOUT Row-Level Security. The secured
-- deployment adds RLS in a separate migration (secured/V2__row_level_security.sql).
-- Keeping the two deployments in separate schemas lets the tests compare the exact same
-- table and queries with RLS as the only difference.

-- Workspaces: one per tenant. The unique constraint (tenant_id, id) is what lets the
-- composite foreign key below stay tenant-aware: a document can only reference a
-- workspace that belongs to the same tenant.
CREATE TABLE workspaces (
    id          UUID        NOT NULL,
    tenant_id   TEXT        NOT NULL,
    name        TEXT        NOT NULL,
    CONSTRAINT pk_workspaces PRIMARY KEY (id),
    CONSTRAINT uq_workspaces_tenant UNIQUE (tenant_id, id)
);

CREATE TABLE documents (
    id            UUID    NOT NULL,
    tenant_id     TEXT    NOT NULL,
    workspace_id  UUID    NOT NULL,
    title         TEXT    NOT NULL,
    content       TEXT    NOT NULL,
    created_by    TEXT    NOT NULL,
    CONSTRAINT pk_documents PRIMARY KEY (id),
    CONSTRAINT uq_documents_tenant UNIQUE (tenant_id, id),
    CONSTRAINT fk_documents_workspace
        FOREIGN KEY (tenant_id, workspace_id)
        REFERENCES workspaces (tenant_id, id)
);

CREATE INDEX idx_documents_tenant_id ON documents (tenant_id);

-- Deterministic fixtures so test assertions are readable.
INSERT INTO workspaces (id, tenant_id, name) VALUES
    ('00000000-0000-0000-0000-00000000001a', 'tenant-a', 'Acme workspace'),
    ('00000000-0000-0000-0000-00000000001b', 'tenant-b', 'Othercorp workspace');

INSERT INTO documents (id, tenant_id, workspace_id, title, content, created_by) VALUES
    ('00000000-0000-0000-0000-0000000000aa', 'tenant-a', '00000000-0000-0000-0000-00000000001a',
     'Q3 plan', 'sensitive tenant-a planning notes', 'ana'),
    ('00000000-0000-0000-0000-0000000000ab', 'tenant-b', '00000000-0000-0000-0000-00000000001b',
     'Othercorp pricing', 'tenant-b private pricing data', 'bob');

-- The application role may read and write data but owns nothing and cannot bypass RLS.
GRANT USAGE ON SCHEMA "${appSchema}" TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA "${appSchema}" TO app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA "${appSchema}"
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user;
