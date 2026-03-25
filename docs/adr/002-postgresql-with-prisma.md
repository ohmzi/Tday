# ADR 002: PostgreSQL with Prisma ORM

**Status:** Accepted  
**Date:** 2024

## Context

The application needs a relational database for user data, tasks with complex recurrence rules, and referential integrity. Options considered:

1. **SQLite** — simple, file-based, no server needed.
2. **PostgreSQL** — full-featured relational database with strong JSON and full-text search support.
3. **MongoDB** — document store, flexible schema.

For the ORM layer: Prisma, Drizzle, TypeORM, or raw SQL.

## Decision

- Use **PostgreSQL 15** as the primary data store.
- Use **Prisma 6** as the ORM with schema-first migrations.
- Enable the `fullTextSearchPostgres` preview feature for future search capabilities.

## Rationale

- PostgreSQL provides ACID transactions, referential integrity, and array types (used for `exdates` on todos).
- Prisma provides type-safe database access with generated TypeScript types from the schema.
- Schema-first migrations give a clear audit trail of database changes.
- The Prisma client extension mechanism enables transparent field encryption without modifying business logic.
- PostgreSQL runs well in Docker and has mature tooling for backups and monitoring.

## Consequences

- **Positive**: Strong data integrity, type-safe queries, easy migration management.
- **Negative**: Prisma does not support automatic down-migrations — rollbacks require manual SQL. Prisma's query API is less flexible than raw SQL for complex joins (can use `$queryRaw` when needed).
- **Trade-off**: Vendor lock-in to PostgreSQL-specific features (array types, full-text search). Acceptable since there are no plans to switch databases.
