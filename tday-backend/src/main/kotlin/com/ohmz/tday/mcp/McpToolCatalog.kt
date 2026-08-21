package com.ohmz.tday.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * One MCP tool. [requiresWrite] is the whole authorization model: a READ-scoped API
 * key may call every tool without it and no tool with it.
 */
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val requiresWrite: Boolean = false,
    val readOnlyHint: Boolean = !requiresWrite,
    val destructiveHint: Boolean = false,
) {
    fun toJson(readOnlyConnection: Boolean): JsonObject = buildJsonObject {
        put("name", JsonPrimitive(name))
        put(
            "description",
            JsonPrimitive(
                if (requiresWrite && readOnlyConnection) "$READ_ONLY_PREFIX $description" else description,
            ),
        )
        put("inputSchema", inputSchema)
        put(
            "annotations",
            buildJsonObject {
                put("readOnlyHint", JsonPrimitive(readOnlyHint))
                put("destructiveHint", JsonPrimitive(destructiveHint))
            },
        )
    }

    companion object {
        const val READ_ONLY_PREFIX = "[unavailable — this connection uses a read-only API key]"
    }
}

/**
 * The tool surface exposed over MCP.
 *
 * Two T'Day concepts drive almost every schema decision here, and neither is
 * guessable from the REST API alone:
 *
 *  - **A date decides the entity.** A task with a due date is a scheduled todo; one
 *    without is a *floater* (the "Anytime" feed). They are separate rows with separate
 *    endpoints, not one row with a nullable date — so `due` being present or absent on
 *    `tday_create_task` picks which one gets written.
 *  - **Lists come in two namespaces.** Scheduled lists hold todos, Anytime lists hold
 *    floaters, and a list name can exist in one and not the other.
 *
 * Task ids are handles — `todo:<id>` or `floater:<id>` — because a bare id can't tell
 * a caller which of the two it addresses, and every write has to pick an endpoint.
 */
object McpToolCatalog {

    const val GET_CONTEXT = "tday_get_context"
    const val FIND_LIST = "tday_find_list"
    const val LIST_TASKS = "tday_list_tasks"
    const val SEARCH_TASKS = "tday_search_tasks"
    const val CREATE_TASK = "tday_create_task"
    const val UPDATE_TASK = "tday_update_task"
    const val COMPLETE_TASK = "tday_complete_task"
    const val DELETE_TASK = "tday_delete_task"
    const val CREATE_LIST = "tday_create_list"

    val tools: List<McpTool> = listOf(
        McpTool(
            name = GET_CONTEXT,
            description = "Who the connected T'Day account is, their timezone, the current server time, " +
                "whether this connection can make changes, and every list in both namespaces " +
                "(scheduled lists and Anytime lists). Call this first in a session: the timezone is " +
                "needed to turn 'tomorrow at 9' into a real time, and the list names are needed before " +
                "putting a task in a list.",
            inputSchema = objectSchema(),
        ),
        McpTool(
            name = FIND_LIST,
            description = "Check whether a list exists by name before using it. Returns whether it was " +
                "found, in which namespace, and close matches when it wasn't. Use this whenever the user " +
                "names a list, so you can tell them it doesn't exist instead of silently creating one.",
            inputSchema = objectSchema(
                required = listOf("name"),
                properties = mapOf(
                    "name" to stringProp("The list name to look for. Matching is case-insensitive and tolerant of typos."),
                    "kind" to enumProp(
                        listOf("scheduled", "anytime", "any"),
                        "Which namespace to search. 'scheduled' holds dated tasks, 'anytime' holds undated ones. Defaults to 'any'.",
                    ),
                ),
            ),
        ),
        McpTool(
            name = LIST_TASKS,
            description = "Read tasks. Recurring series are expanded into their actual occurrences, " +
                "cancelled occurrences are omitted, and every time is reported in the user's timezone.",
            inputSchema = objectSchema(
                properties = mapOf(
                    "view" to enumProp(
                        listOf("today", "overdue", "upcoming", "anytime", "all"),
                        "'today' = due today. 'overdue' = past due and still pending. 'upcoming' = due from now " +
                            "through the next 14 days (or the from/to window). 'anytime' = undated tasks. " +
                            "'all' = everything. Defaults to 'today'.",
                    ),
                    "from" to stringProp("Window start as a date or date-time (YYYY-MM-DD or YYYY-MM-DDTHH:mm), in the user's timezone. Only used with view 'upcoming' or 'all'."),
                    "to" to stringProp("Window end, same format as 'from'."),
                    "listName" to stringProp("Only tasks in the list with this name."),
                    "includeCompleted" to boolProp("Include completed tasks. Defaults to false."),
                    "limit" to intProp("Maximum tasks to return. Defaults to 100."),
                ),
            ),
        ),
        McpTool(
            name = SEARCH_TASKS,
            description = "Find tasks whose title or notes match a search term, across both scheduled and " +
                "Anytime tasks. Use this to locate a task the user described in words before editing it.",
            inputSchema = objectSchema(
                required = listOf("query"),
                properties = mapOf(
                    "query" to stringProp("Words to look for in the task title or notes."),
                    "includeCompleted" to boolProp("Also search completed history. Defaults to false."),
                    "limit" to intProp("Maximum tasks to return. Defaults to 25."),
                ),
            ),
        ),
        McpTool(
            name = CREATE_TASK,
            description = "Create a task. Supply 'due' when the user gave a date or time and the task " +
                "becomes a scheduled task; omit 'due' entirely when they didn't and it becomes an Anytime " +
                "task instead. Do not invent a date to make a task schedulable. If you name a list that " +
                "doesn't exist, nothing is created and you get back the existing list names — tell the " +
                "user and ask before retrying with createListIfMissing.",
            requiresWrite = true,
            inputSchema = objectSchema(
                required = listOf("title"),
                properties = mapOf(
                    "title" to stringProp("The task title."),
                    "notes" to stringProp("Longer description or notes for the task."),
                    "due" to stringProp(
                        "When the task is due, in the user's timezone: 'YYYY-MM-DDTHH:mm', or 'YYYY-MM-DD' " +
                            "for a whole day, or a full ISO-8601 instant ending in Z. Leave this out for an " +
                            "undated Anytime task.",
                    ),
                    "timeZone" to stringProp("IANA timezone for interpreting 'due'. Defaults to the user's own timezone."),
                    "recurrence" to stringProp(
                        "RFC-5545 recurrence rule for a repeating task, e.g. 'RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO'. Requires 'due'.",
                    ),
                    "priority" to enumProp(listOf("Low", "Medium", "High"), "Task priority. Defaults to Low."),
                    "listName" to stringProp("Name of the list to file this under. Must already exist unless createListIfMissing is true."),
                    "listId" to stringProp("List id, if you already have it from tday_get_context or tday_find_list. Takes precedence over listName."),
                    "createListIfMissing" to boolProp(
                        "Create the named list when it doesn't exist. Defaults to false — only set this once the user has confirmed they want a new list.",
                    ),
                ),
            ),
        ),
        McpTool(
            name = UPDATE_TASK,
            description = "Change an existing task. Adding a due date to an Anytime task turns it into a " +
                "scheduled task; clearing the due date turns a scheduled task back into an Anytime one " +
                "(this is how T'Day models 'unscheduling' — its list membership does not carry across). " +
                "For a repeating task, pass occurrenceDate to change one occurrence and leave the rest alone.",
            requiresWrite = true,
            inputSchema = objectSchema(
                required = listOf("taskId"),
                properties = mapOf(
                    "taskId" to taskIdProp(),
                    "title" to stringProp("New title."),
                    "notes" to stringProp("New notes."),
                    "priority" to enumProp(listOf("Low", "Medium", "High"), "New priority."),
                    "due" to stringProp("New due date/time, in the same formats tday_create_task accepts."),
                    "clearDue" to boolProp("Remove the due date, making this an undated Anytime task. Not possible for a repeating task."),
                    "recurrence" to stringProp("New RFC-5545 recurrence rule."),
                    "clearRecurrence" to boolProp("Stop the task repeating."),
                    "listName" to stringProp("Move the task to the list with this name."),
                    "listId" to stringProp("Move the task to this list id. Takes precedence over listName."),
                    "createListIfMissing" to boolProp("Create the named list when it doesn't exist. Defaults to false."),
                    "pinned" to boolProp("Pin or unpin the task."),
                    "occurrenceDate" to occurrenceDateProp("Edit only this occurrence of a repeating task."),
                ),
            ),
        ),
        McpTool(
            name = COMPLETE_TASK,
            description = "Mark a task done, or undo that. For a repeating task, pass occurrenceDate to " +
                "complete a single occurrence rather than the whole series.",
            requiresWrite = true,
            inputSchema = objectSchema(
                required = listOf("taskId"),
                properties = mapOf(
                    "taskId" to taskIdProp(),
                    "completed" to boolProp("True to complete, false to reopen. Defaults to true."),
                    "occurrenceDate" to occurrenceDateProp("Complete only this occurrence of a repeating task."),
                ),
            ),
        ),
        McpTool(
            name = DELETE_TASK,
            description = "Delete a task permanently. For a repeating task, pass occurrenceDate to cancel " +
                "one occurrence and keep the rest of the series; without it the whole series is deleted. " +
                "Confirm with the user before deleting anything they didn't explicitly ask to remove.",
            requiresWrite = true,
            destructiveHint = true,
            inputSchema = objectSchema(
                required = listOf("taskId"),
                properties = mapOf(
                    "taskId" to taskIdProp(),
                    "occurrenceDate" to occurrenceDateProp("Cancel only this occurrence, keeping the series."),
                ),
            ),
        ),
        McpTool(
            name = CREATE_LIST,
            description = "Create a list. 'scheduled' lists hold dated tasks and 'anytime' lists hold " +
                "undated ones — pick the one matching the tasks that will go in it. Returns the existing " +
                "list unchanged if the name is already taken in that namespace.",
            requiresWrite = true,
            inputSchema = objectSchema(
                required = listOf("name", "kind"),
                properties = mapOf(
                    "name" to stringProp("Name for the new list."),
                    "kind" to enumProp(listOf("scheduled", "anytime"), "'scheduled' for dated tasks, 'anytime' for undated ones."),
                    "color" to stringProp("Optional colour. One of: $LIST_COLORS."),
                    "reusable" to boolProp("Anytime lists only: a reusable list can be reset to run again, like a packing list."),
                ),
            ),
        ),
    )

    private val byName: Map<String, McpTool> = tools.associateBy { it.name }

    fun find(name: String): McpTool? = byName[name]

    /**
     * Prepended to every session. Teaches the two things a model cannot infer from the
     * tool schemas alone and will otherwise get wrong.
     */
    fun instructions(canWrite: Boolean): String = buildString {
        append(
            """
            T'Day is a personal task planner. Two things about how it models tasks:

            1. A task either has a due date or it doesn't, and that decides what it is. Dated tasks are
               "scheduled" and show up in Today, the calendar, and reminders. Undated tasks are "Anytime"
               tasks and live in their own feed. Adding a date to an Anytime task converts it to a
               scheduled one; removing the date converts it back. Never invent a date the user didn't give.
            2. Lists come in two separate namespaces — scheduled lists and Anytime lists. A name can exist
               in one and not the other. Check with tday_find_list before filing a task into a named list,
               and don't create a list unless the user asked for it.

            Call tday_get_context at the start of a session for the user's timezone and their lists.
            """.trimIndent(),
        )
        if (!canWrite) {
            append("\n\n")
            append(
                "This connection uses a READ-only T'Day API key. You can read tasks and lists but cannot " +
                    "create, edit, complete or delete anything. If the user asks for a change, tell them " +
                    "the key is read-only and that a Full-access key from Settings → Dashboard access is needed.",
            )
        }
    }

    private const val LIST_COLORS =
        "RED, ORANGE, YELLOW, LIME, BLUE, PURPLE, PINK, TEAL, CORAL, GOLD, DEEP_BLUE, ROSE, LIGHT_RED, BRICK, SLATE"

    private fun taskIdProp(): JsonObject = stringProp(
        "Task handle from a read tool: 'todo:<id>' for a scheduled task or 'floater:<id>' for an Anytime task.",
    )

    private fun occurrenceDateProp(description: String): JsonObject = stringProp(
        "$description Use the occurrence's own date-time exactly as a read tool reported it.",
    )

    private fun objectSchema(
        required: List<String> = emptyList(),
        properties: Map<String, JsonObject> = emptyMap(),
    ): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put(
            "properties",
            buildJsonObject { properties.forEach { (name, schema) -> put(name, schema) } },
        )
        put("required", JsonArray(required.map { JsonPrimitive(it) }))
        put("additionalProperties", JsonPrimitive(false))
    }

    private fun stringProp(description: String): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("string"))
        put("description", JsonPrimitive(description))
    }

    private fun boolProp(description: String): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("boolean"))
        put("description", JsonPrimitive(description))
    }

    private fun intProp(description: String): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("integer"))
        put("description", JsonPrimitive(description))
    }

    private fun enumProp(values: List<String>, description: String): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("string"))
        put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
        put("description", JsonPrimitive(description))
    }
}
