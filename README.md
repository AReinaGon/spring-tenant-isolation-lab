# Tenant Isolation Lab

A small, reproducible lab behind the article *"El gateway validó el token. La base de datos dijo
que no"*.

**Thesis:** a correctly validated JWT and a valid role do not guarantee multitenant isolation. Spring
Security must authorize the operation, Hibernate can automate the tenant discriminator in ORM queries,
and PostgreSQL Row-Level Security must contain a query that forgets that discriminator.

The lab shows, with automated tests, the initial cross-tenant leak and how each layer closes it:
the perimeter and RBAC alone do not stop a buggy query, Hibernate's `@TenantId` reduces the risk,
a native query escapes the ORM filter, and Row-Level Security contains it for both reads and writes.

> ⚠️ **This lab contains deliberately unsafe code as teaching material.** The `/lab/**` endpoints and
> the entities/repositories behind them reproduce a buggy `findById` and unsafe native queries on
> purpose, so the failure can be observed. They are **not** part of the secure-by-default surface and
> must not be copied into production code. The full evidence notes live in the local, git-ignored
> `docs/evidence.md` file.

## Requirements

- JDK 25 (the build pins `java.version=25`; any 25 JDK works, e.g. Corretto or Temurin).
- Docker (Testcontainers starts a real PostgreSQL 17 container; no manual database setup).
- Internet on first run (Maven wrapper downloads Maven 3.9.9; Maven downloads dependencies; Docker
  pulls the `postgres:17-alpine` image).

## Run everything

```bash
./mvnw clean verify        # POSIX
.\mvnw.cmd clean verify    # Windows
```

That single command starts a PostgreSQL container, runs the Flyway migrations for both deployments,
and executes the whole suite (34 tests). No manual steps.

## What the lab implements

```
Internet → CDN/WAF → Security/API Gateway → LB/Ingress → │ Document API → Hibernate → PostgreSQL + RLS │
                                                        └───────────── lab boundary ───────────────────┘
```

The lab implements only from `Document API` down to PostgreSQL. CDN, WAF, gateway, ingress, service
mesh, frontend and identity provider are out of scope; a test request stands for a request that already
crossed the perimeter. `Document API` still re-validates the JWT (signature, expiry, issuer, audience):
being behind a gateway grants no implicit trust.

The tenant id is never read from a query parameter or a client header. It is derived exclusively from
the validated JWT claims, carried by the `TenantContext`, and propagated to Hibernate and PostgreSQL by
a transaction aspect that runs `set_config('app.current_tenant', ?, true)` on the exact connection the
queries will use.

## The five scenes

The suite is split into two deployments via two schemas and two test profiles:

| Scene | Test | Naive schema (no RLS) | Secured schema (RLS) |
|---|---|---|---|
| 1. Perimeter + RBAC are not enough: a buggy `findById` leaks tenant-b's document | `NaiveLeakTest.perimeterValidationAndRbacDoNotContainVulnerableFindById` | **leaks (200)** | contained (404) |
| 2. Hibernate reduces the risk: `@TenantId` filters the ORM query | `NaiveLeakTest.ormQueryIsFilteredToTheCurrentTenant` | filtered (404) | filtered (404) |
| 3. The ORM limit: a native query is not auto-filtered | `NaiveLeakTest.nativeQueryBypassesHibernateTenantFilter` | **leaks (200)** | contained (404) |
| 4. The database contains the failure | `SecuredRlsTest.rlsContainsNativeQuery`, `rlsContainsVulnerableFindById` | — | contained (404) |
| 5. Writes and pool: `WITH CHECK` rejects cross-tenant writes; pooled connections do not inherit the tenant | `SecuredRlsTest.rlsWithCheckRejectsCrossTenantInsert`, `updateMovingDocumentToAnotherTenantIsRejectedByWithCheck`, `SecuredPoolTest.*` | — | rejected |

The tests also cover: real JWT cryptography (`401` for bad signature, wrong audience/issuer, expired,
missing `tenant_id`; `403` for valid identity without the required role/scope), the tenant-aware foreign
key between workspaces and documents, fail-closed behaviour when no tenant is present, the second-level
cache encoding the tenant in its key (a cached tenant-a entity is never returned to tenant-b), and the
honest caveat that a superuser or `BYPASSRLS` role bypasses the policies.

## Domain and endpoints

Two tenants (`tenant-a`, `tenant-b`), one workspace per tenant, one fixture document per tenant, and a
user `ana` in `tenant-a` with role `EDITOR` and scope `documents:read`.

| Endpoint | Path | Notes |
|---|---|---|
| `GET /documents/{id}` | secure read | tenant-aware ORM + RLS |
| `POST /documents` | secure create | tenant id filled by Hibernate from the context |
| `GET /lab/vulnerable/documents/{id}` | **demo** buggy `findById` | entity without `@TenantId` |
| `GET /lab/native/documents/{id}` | **demo** native query | bypasses the ORM tenant filter |
| `POST /lab/raw/documents` | **demo** unsafe write | trusts a caller-supplied tenant id |

Authorization is method-level: reads require `ROLE_EDITOR` and `SCOPE_documents:read`; writes require
`ROLE_EDITOR`.

## Technical stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 25 (Corretto) | base language |
| Spring Boot | 4.0.6 | framework |
| Spring MVC | bundled | blocking servlet stack (no WebFlux) |
| Spring Security OAuth2 Resource Server | 7.0.5 | JWT validation + method security |
| Spring Data JPA / Hibernate | 7.2.12 | ORM, `@TenantId` discriminator, second-level cache |
| JCache / Ehcache | managed by Boot (3.11.1) | Hibernate second-level cache provider |
| PostgreSQL | 17 (Testcontainers) | real database, Row-Level Security |
| Flyway | 11.14.1 | schema, fixtures, grants, RLS policies |
| Testcontainers | 2.0.5 | PostgreSQL container |
| JUnit 5 / AssertJ / MockMvc | managed | tests |

## Repository layout

```
src/main/java/com/areina/tenantlab/
├── config/          # security, tenant persistence wiring, error mapping
├── security/        # JWT → TenantAuthenticationToken, TenantContextFilter
├── tenant/          # TenantContext, TenantResolver, TenantTransactionAspect, TenantProbe
└── document/        # entities (incl. the vulnerable one), repositories, services, controllers
src/main/resources/
├── db/migration/common/    # schema + fixtures + grants (shared by both deployments)
├── db/migration/secured/   # Row-Level Security policies (secured deployment only)
└── keys/                   # the lab issuer's public key (JWT verification)
src/test/                   # integration tests, TestJwtFactory, container bootstrap
docs/                       # evidence.md — local editorial material, git-ignored
```

The RSA private key under `src/test/resources/keys/` is a deliberate, test-only fixture. It signs the
short-lived JWTs used by the suite and must never be reused as a deployable secret. Runtime code only
contains the matching public key.

## Related documents

The editorial brief and evidence notes are maintained outside the public repository. The published
article will be linked here when it is available.

## License

[MIT](LICENSE), Copyright (c) 2026 Antonio Reina.
