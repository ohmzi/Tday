package com.ohmz.tday.shared.model

import kotlinx.serialization.Serializable

/**
 * Versioned, portable snapshot of one user's data. Emitted by `GET /api/export`
 * and consumed by `POST /api/import`; also the on-disk shape each client writes
 * for "Download my data".
 *
 * Descriptions travel as plaintext (the export decrypts server-side ciphertext),
 * so a bundle is portable across servers that use different encryption keys — the
 * importing server re-encrypts on its own boundary.
 *
 * Compose existing per-resource DTOs so the wire shape stays in lockstep with the
 * live API. [schemaVersion] gates forward compatibility: an importer refuses a
 * bundle whose schemaVersion exceeds the one it understands, and fills missing
 * fields from defaults for older bundles.
 */
@Serializable
data class TdayExport(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    /** ISO-8601 instant the bundle was produced. */
    val exportedAt: String? = null,
    /** Marketing version of the producing app, for diagnostics. */
    val appVersion: String? = null,
    /** Where it came from: "server", "local-android", "local-ios". */
    val source: String? = null,
    val lists: List<ListDto> = emptyList(),
    val floaterLists: List<FloaterListDto> = emptyList(),
    val todos: List<ExportedTodoDto> = emptyList(),
    val floaters: List<FloaterDto> = emptyList(),
    val completedTodos: List<CompletedTodoDto> = emptyList(),
    val completedFloaters: List<CompletedFloaterDto> = emptyList(),
    val preferences: PreferencesDto? = null,
) {
    companion object {
        /** Bump whenever the bundle gains a field older importers must not silently drop. */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/**
 * A todo plus the recurrence state the flat [TodoDto] doesn't carry: its
 * cancelled-occurrence [exdates] and per-occurrence [instances] overrides.
 * Nesting the instances keeps them tied to their parent so an id remap on
 * import moves both together.
 */
@Serializable
data class ExportedTodoDto(
    val todo: TodoDto,
    /** ISO-8601 datetimes of cancelled recurrence occurrences. */
    val exdates: List<String> = emptyList(),
    val instances: List<TodoInstanceDto> = emptyList(),
)

/** One materialized override of a recurring todo occurrence (`todo_instances`). */
@Serializable
data class TodoInstanceDto(
    val id: String,
    val recurId: String = "",
    val instanceDate: String,
    val overriddenTitle: String? = null,
    val overriddenDescription: String? = null,
    val overriddenPriority: String? = null,
    val overriddenDue: String? = null,
    val completedAt: String? = null,
)

/**
 * Body of `POST /api/import`. [dryRun] validates and returns the counts that
 * would be written without touching the database — the preview a trust screen
 * shows before the user confirms.
 */
@Serializable
data class ImportRequest(
    val export: TdayExport,
    val dryRun: Boolean = false,
    val includeCompleted: Boolean = true,
    val includePreferences: Boolean = true,
)

/** What an import wrote (or, for a dry run, would write). */
@Serializable
data class ImportCounts(
    val lists: Int = 0,
    val floaterLists: Int = 0,
    val todos: Int = 0,
    val floaters: Int = 0,
    val todoInstances: Int = 0,
    val completedTodos: Int = 0,
    val completedFloaters: Int = 0,
    /** How many colliding ids were minted fresh so nothing was overwritten. */
    val remappedIds: Int = 0,
    val preferencesApplied: Boolean = false,
)

@Serializable
data class ImportResponse(
    val dryRun: Boolean,
    val imported: ImportCounts,
    val message: String? = null,
)
