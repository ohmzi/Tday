package com.ohmz.tday.db

import org.jetbrains.exposed.sql.Transaction

/**
 * The exact enum-type bootstrap [com.ohmz.tday.config.DatabaseConfig.init] runs in production
 * (the `IF NOT EXISTS` DO-block), reused by every test that talks to a REAL Postgres instance
 * (Testcontainers) instead of the H2 double [TestDatabase] provides. Call inside an open
 * transaction against that real connection.
 *
 * Not part of [TestDatabase]: that object's `fresh()` is H2-specific (a `CREATE DOMAIN`
 * shim stands in for these types there), so a real-Postgres-only helper belongs on its own.
 */
fun Transaction.bootstrapProductionPgEnums() {
    listOf(
        "\"UserRole\"" to listOf("ADMIN", "USER"),
        "\"ApprovalStatus\"" to listOf("APPROVED", "PENDING"),
        "\"SortBy\"" to listOf("due", "priority"),
        "\"GroupBy\"" to listOf("due", "priority", "rrule", "project"),
        "\"Direction\"" to listOf("Ascending", "Descending"),
        // `Lowest` is placed BEFORE `Low` to match V28__add_lowest_priority.sql's
        // `ALTER TYPE "Priority" ADD VALUE 'Lowest' BEFORE 'Low'` -- see that migration for why
        // the ordinal position matters (raw-SQL `ORDER BY priority DESC` sites rely on it).
        "\"Priority\"" to listOf("Lowest", "Low", "Medium", "High"),
        "\"ProjectColor\"" to listOf(
            "RED", "ORANGE", "YELLOW", "LIME", "BLUE", "PURPLE", "PINK", "TEAL",
            "CORAL", "GOLD", "DEEP_BLUE", "ROSE", "LIGHT_RED", "BRICK", "SLATE",
        ),
        "\"DefaultHomeScreen\"" to listOf("scheduled", "floater"),
    ).forEach { (name, values) ->
        val valList = values.joinToString(", ") { "'$it'" }
        exec(
            "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = " +
                "${name.replace("\"", "'")}) THEN CREATE TYPE $name AS ENUM ($valList); END IF; END $$;",
        )
    }
}
