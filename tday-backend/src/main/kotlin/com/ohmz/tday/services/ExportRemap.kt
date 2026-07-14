package com.ohmz.tday.services

import com.ohmz.tday.shared.model.TdayExport

/**
 * Pure duplicate-id remapper for imports. Any primary-key id that already exists
 * for the target user — or repeats within the bundle — is minted fresh and every
 * reference to it rewritten, so an import always *adds* rows and never overwrites
 * existing ones (the "restore alongside" / merge semantics the trust screen
 * promises). Free of DB/Exposed so it unit-tests with a fake `idExists` predicate
 * and a deterministic `newId`.
 */
object ExportRemap {
    data class Result(val export: TdayExport, val remappedIds: Int)

    fun remapCollisions(
        export: TdayExport,
        idExists: (String) -> Boolean,
        newId: () -> String,
    ): Result {
        // ids already taken: existing DB rows plus ids assigned earlier this run.
        val used = HashSet<String>()
        // oldId -> assigned id for reference rewrites. First occurrence wins:
        // identity when the PK didn't collide, the fresh id when it did.
        val idMap = HashMap<String, String>()
        var remappedCount = 0

        // Assign a unique id for one primary-key slot. A PK that collides with the
        // DB — or with an id already taken this run, i.e. a duplicate PK inside the
        // bundle — is minted fresh. `idExists` is a finite set of existing DB ids, so
        // the retry loop terminates; freshly minted cuids won't be in it.
        fun assign(oldId: String): String {
            val collides = idExists(oldId) || oldId in used
            val finalId = if (!collides) {
                oldId
            } else {
                var candidate = newId()
                while (idExists(candidate) || candidate in used) {
                    candidate = newId()
                }
                candidate
            }
            used.add(finalId)
            if (finalId != oldId) remappedCount++
            // A duplicate PK keeps its own fresh id but must not repoint references
            // that already resolved to the first occurrence.
            idMap.putIfAbsent(oldId, finalId)
            return finalId
        }

        // Lists first so todos/floaters/completed rows can rewrite their list refs.
        val lists = export.lists.map { it.copy(id = assign(it.id)) }
        val floaterLists = export.floaterLists.map { it.copy(id = assign(it.id)) }

        val todos = export.todos.map { exported ->
            val newTodoId = assign(exported.todo.id)
            exported.copy(
                todo = exported.todo.copy(
                    id = newTodoId,
                    listID = exported.todo.listID?.let { idMap[it] ?: it },
                ),
                // Instances travel with their parent; their own PKs still need remap.
                instances = exported.instances.map { it.copy(id = assign(it.id)) },
            )
        }
        val floaters = export.floaters.map { floater ->
            floater.copy(
                id = assign(floater.id),
                listID = floater.listID?.let { idMap[it] ?: it },
            )
        }
        val completedTodos = export.completedTodos.map { completed ->
            completed.copy(
                id = assign(completed.id),
                listID = completed.listID?.let { idMap[it] ?: it },
                originalTodoID = completed.originalTodoID?.let { idMap[it] ?: it },
            )
        }
        val completedFloaters = export.completedFloaters.map { completed ->
            completed.copy(
                id = assign(completed.id),
                listID = completed.listID?.let { idMap[it] ?: it },
                originalFloaterID = completed.originalFloaterID?.let { idMap[it] ?: it },
            )
        }

        return Result(
            export = export.copy(
                lists = lists,
                floaterLists = floaterLists,
                todos = todos,
                floaters = floaters,
                completedTodos = completedTodos,
                completedFloaters = completedFloaters,
            ),
            remappedIds = remappedCount,
        )
    }
}
