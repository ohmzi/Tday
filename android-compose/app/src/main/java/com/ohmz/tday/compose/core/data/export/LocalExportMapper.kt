package com.ohmz.tday.compose.core.data.export

import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.shared.model.CompletedFloaterDto
import com.ohmz.tday.shared.model.CompletedTodoDto
import com.ohmz.tday.shared.model.ExportedTodoDto
import com.ohmz.tday.shared.model.FloaterDto
import com.ohmz.tday.shared.model.FloaterListDto
import com.ohmz.tday.shared.model.ListDto
import com.ohmz.tday.shared.model.PreferencesDto
import com.ohmz.tday.shared.model.TdayExport
import com.ohmz.tday.shared.model.TodoDto
import java.time.Instant

/**
 * Builds a [TdayExport] from the local offline cache, for exporting in Local
 * Mode (Server Mode uses the backend's complete `GET /api/export` instead).
 *
 * Best-effort: the Android cache doesn't persist recurrence exdates/instance
 * overrides or the completed-on-time flag, so those come out empty/defaulted.
 * Local Mode has no sharing, so every cached row is the user's own — no owner
 * filtering needed. Pure (no Android/IO), so it unit-tests directly.
 */
object LocalExportMapper {

    fun toExport(
        state: OfflineSyncState,
        source: String,
        exportedAtEpochMs: Long,
    ): TdayExport {
        val exportedAtIso = iso(exportedAtEpochMs)
        return TdayExport(
            exportedAt = exportedAtIso,
            source = source,
            lists = state.lists.map { rec ->
                ListDto(
                    id = rec.id,
                    name = rec.name,
                    color = rec.color,
                    iconKey = rec.iconKey,
                    updatedAt = iso(rec.updatedAtEpochMs),
                    createdAt = iso(rec.createdAtEpochMs),
                )
            },
            floaterLists = state.floaterLists.map { rec ->
                FloaterListDto(
                    id = rec.id,
                    name = rec.name,
                    color = rec.color,
                    iconKey = rec.iconKey,
                    updatedAt = iso(rec.updatedAtEpochMs),
                    createdAt = iso(rec.createdAtEpochMs),
                )
            },
            todos = state.todos.map { rec ->
                ExportedTodoDto(
                    todo = TodoDto(
                        id = rec.canonicalId,
                        title = rec.title,
                        description = rec.description,
                        pinned = rec.pinned,
                        priority = rec.priority,
                        due = iso(rec.dueEpochMs) ?: exportedAtIso,
                        rrule = rec.rrule,
                        timeZone = "UTC",
                        instanceDate = iso(rec.instanceDateEpochMs),
                        completed = rec.completed,
                        listID = rec.listId,
                        updatedAt = iso(rec.updatedAtEpochMs),
                    ),
                )
            },
            floaters = state.floaters.map { rec ->
                FloaterDto(
                    id = rec.canonicalId,
                    title = rec.title,
                    description = rec.description,
                    pinned = rec.pinned,
                    priority = rec.priority,
                    completed = rec.completed,
                    listID = rec.listId,
                    updatedAt = iso(rec.updatedAtEpochMs),
                )
            },
            completedTodos = state.completedItems.map { rec ->
                CompletedTodoDto(
                    id = rec.id,
                    originalTodoID = rec.originalTodoId,
                    title = rec.title,
                    description = rec.description,
                    priority = rec.priority,
                    due = iso(rec.dueEpochMs) ?: iso(rec.completedAtEpochMs) ?: exportedAtIso,
                    completedAt = iso(rec.completedAtEpochMs),
                    // Not tracked locally; the backend recomputes on its own rows.
                    completedOnTime = true,
                    rrule = rec.rrule,
                    instanceDate = iso(rec.instanceDateEpochMs),
                    listID = rec.listId,
                    listName = rec.listName,
                    listColor = rec.listColor,
                )
            },
            completedFloaters = state.completedFloaters.map { rec ->
                CompletedFloaterDto(
                    id = rec.id,
                    originalFloaterID = rec.originalFloaterId,
                    title = rec.title,
                    description = rec.description,
                    priority = rec.priority,
                    completedAt = iso(rec.completedAtEpochMs),
                    listID = rec.listId,
                    listName = rec.listName,
                    listColor = rec.listColor,
                )
            },
            preferences = PreferencesDto(aiSummaryEnabled = state.aiSummaryEnabled),
        )
    }

    private fun iso(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

    private fun iso(epochMs: Long?): String? = epochMs?.let { Instant.ofEpochMilli(it).toString() }
}
