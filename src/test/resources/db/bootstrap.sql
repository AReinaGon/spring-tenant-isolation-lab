-- Container bootstrap, executed by Testcontainers as the container superuser before any
-- Spring connection is opened. It creates the two database roles used by the lab:
--
--   * migrator  - owns the schema objects and runs the Flyway migrations (DDL + data + policies).
--   * app_user  - the runtime role of the application. Plain LOGIN role, no SUPERUSER, no
--                 BYPASSRLS, never the owner of the tables, so Row-Level Security applies.
--
-- The database name is the one set on the container (lab); GRANT CREATE lets migrator create
-- the per-profile schemas (naive/secured) that Flyway manages.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'migrator') THEN
        CREATE ROLE migrator LOGIN PASSWORD 'migrator';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_user') THEN
        CREATE ROLE app_user LOGIN PASSWORD 'app_user';
    END IF;
END $$;

GRANT CREATE ON DATABASE lab TO migrator;
