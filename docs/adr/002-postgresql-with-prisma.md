# ADR 002: PostgreSQL with Exposed ORM and Flyway Migrations

**Status:** Accepted (supersedes original Prisma decision)
**Date:** 2025

## Context

The application needs a relational database for user data, tasks with complex recurrence rules, and referential integrity. The original architecture used Prisma ORM with TypeScript. When the backend migrated to Kotlin/Ktor, a Kotlin-native ORM was needed.

Options considered for the ORM layer:
1. **JetBrains Exposed** — Kotlin-native DSL and DAO APIs, lightweight, coroutine-friendly.
2. **Hibernate/JPA** — mature but heavy, XML/annotation configuration overhead.
3. **jOOQ** — powerful SQL DSL but requires code generation from the schema.
4. **Raw JDBC** — maximum control but verbose and error-prone.

For migrations:
1. **Flyway** — SQL-based, version-numbered migrations. Industry standard for JVM.
2. **Liquibase** — XML/YAML/JSON changelogs. More complex than needed.

## Decision

- Keep **PostgreSQL 15** as the primary data store.
- Use **JetBrains Exposed 0.57.0** (DSL/Table API) for type-safe database access.
- Use **HikariCP** for connection pooling.
- Use **Flyway** for schema migrations with `baselineOnMigrate=true` to bridge the legacy Prisma-managed schema.

## Rationale

- PostgreSQL provides ACID transactions, referential integrity, array types (used for `exdates`), and custom enum types.
- Exposed provides type-safe queries with Kotlin DSL — table definitions serve as both schema documentation and query API.
- Exposed is lightweight and coroutine-compatible, fitting well with Ktor's architecture.
- HikariCP is the standard JVM connection pool with excellent performance characteristics.
- Flyway's SQL-based migrations are transparent and reviewable — each migration is a plain `.sql` file.
- `baselineOnMigrate` allowed a smooth transition from the Prisma-managed schema without data loss.

## Consequences

- **Positive**: Strong data integrity, type-safe queries in Kotlin, transparent SQL migrations, shared Exposed table definitions serve as living schema documentation.
- **Negative**: Flyway does not support automatic down-migrations — rollbacks require manual SQL. Exposed's DSL is less flexible than raw SQL for very complex queries (can use raw SQL when needed).
- **Trade-off**: Vendor lock-in to PostgreSQL-specific features (array types, custom enums). Acceptable since there are no plans to switch databases.
