package com.ohmz.tday.mcp

import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Routes a `tools/call` to [TdayMcpService], and owns the scope guard.
 *
 * The guard is the reason MCP is exempt from the pipeline's read-only method check
 * (`plugins/Security.kt`): every MCP message is a POST, so authorization has to be
 * decided per tool rather than per HTTP verb. It fails closed — a tool that writes
 * runs only when [McpCallContext.canWrite] is explicitly true.
 */
class McpToolDispatcher(private val service: TdayMcpService) {

    private val logger = LoggerFactory.getLogger(McpToolDispatcher::class.java)

    suspend fun call(ctx: McpCallContext, toolName: String, arguments: JsonObject): McpToolResult {
        val tool = McpToolCatalog.find(toolName)
            ?: return McpToolResult.failed(
                "No T'Day tool called \"$toolName\". Available: ${McpToolCatalog.tools.joinToString(", ") { it.name }}.",
            )

        if (tool.requiresWrite && !ctx.canWrite) return readOnlyRefusal()

        return try {
            dispatch(ctx, tool.name, arguments)
        } catch (e: Exception) {
            // A tool failure has to reach the model as a tool result, not a JSON-RPC
            // error, or it just looks like the connection broke.
            logger.error("MCP tool {} failed", tool.name, e)
            McpToolResult.failed("T'Day hit an unexpected error running $toolName.")
        }
    }

    private suspend fun dispatch(ctx: McpCallContext, name: String, args: JsonObject): McpToolResult = when (name) {
        McpToolCatalog.GET_CONTEXT -> service.getContext(ctx)

        McpToolCatalog.FIND_LIST -> {
            val listName = args.stringArg("name")
                ?: return missingArgument("name", name)
            service.findList(ctx, listName, args.stringArg("kind"))
        }

        McpToolCatalog.LIST_TASKS -> service.listTasks(
            ctx = ctx,
            view = args.stringArg("view") ?: TaskView.TODAY.value,
            from = args.stringArg("from"),
            to = args.stringArg("to"),
            listName = args.stringArg("listName"),
            includeCompleted = args.boolArg("includeCompleted") ?: false,
            limit = args.intArg("limit") ?: DEFAULT_LIST_LIMIT,
        )

        McpToolCatalog.SEARCH_TASKS -> {
            val query = args.stringArg("query") ?: return missingArgument("query", name)
            service.searchTasks(
                ctx = ctx,
                query = query,
                includeCompleted = args.boolArg("includeCompleted") ?: false,
                limit = args.intArg("limit") ?: DEFAULT_SEARCH_LIMIT,
            )
        }

        McpToolCatalog.CREATE_TASK -> {
            val title = args.stringArg("title") ?: return missingArgument("title", name)
            val due = args.stringArg("due")
            service.createTask(
                ctx,
                CreateTaskArgs(
                    title = title,
                    notes = args.stringArg("notes"),
                    due = due,
                    dueWasDateOnly = due != null && McpDates.isDateOnly(due),
                    recurrence = args.stringArg("recurrence"),
                    priority = args.stringArg("priority") ?: DEFAULT_PRIORITY,
                    listName = args.stringArg("listName"),
                    listId = args.stringArg("listId"),
                    createListIfMissing = args.boolArg("createListIfMissing") ?: false,
                ),
            )
        }

        McpToolCatalog.UPDATE_TASK -> {
            val taskId = args.stringArg("taskId") ?: return missingArgument("taskId", name)
            service.updateTask(
                ctx,
                UpdateTaskArgs(
                    taskId = taskId,
                    title = args.stringArg("title"),
                    notes = args.stringArg("notes"),
                    priority = args.stringArg("priority"),
                    due = args.stringArg("due"),
                    clearDue = args.boolArg("clearDue") ?: false,
                    recurrence = args.stringArg("recurrence"),
                    clearRecurrence = args.boolArg("clearRecurrence") ?: false,
                    listName = args.stringArg("listName"),
                    listId = args.stringArg("listId"),
                    createListIfMissing = args.boolArg("createListIfMissing") ?: false,
                    pinned = args.boolArg("pinned"),
                    occurrenceDate = args.stringArg("occurrenceDate"),
                ),
            )
        }

        McpToolCatalog.COMPLETE_TASK -> {
            val taskId = args.stringArg("taskId") ?: return missingArgument("taskId", name)
            service.completeTask(
                ctx = ctx,
                taskId = taskId,
                completed = args.boolArg("completed") ?: true,
                occurrenceDate = args.stringArg("occurrenceDate"),
            )
        }

        McpToolCatalog.DELETE_TASK -> {
            val taskId = args.stringArg("taskId") ?: return missingArgument("taskId", name)
            service.deleteTask(ctx, taskId, args.stringArg("occurrenceDate"))
        }

        McpToolCatalog.CREATE_LIST -> {
            val listName = args.stringArg("name") ?: return missingArgument("name", name)
            val kind = args.stringArg("kind") ?: return missingArgument("kind", name)
            service.createList(
                ctx = ctx,
                name = listName,
                kind = kind,
                color = args.stringArg("color"),
                reusable = args.boolArg("reusable") ?: false,
            )
        }

        else -> McpToolResult.failed("No T'Day tool called \"$name\".")
    }

    private fun missingArgument(argument: String, tool: String): McpToolResult =
        McpToolResult.failed("$tool needs a \"$argument\" argument.")

    private fun readOnlyRefusal(): McpToolResult = McpToolResult.failed(READ_ONLY_MESSAGE)

    companion object {
        const val READ_ONLY_MESSAGE =
            "This T'Day API key is read-only (scope: READ). Creating, editing, completing and deleting " +
                "tasks require a Full-access key. Generate one in T'Day → Settings → Dashboard access → " +
                "Full access, then update your MCP connection. Tell the user this rather than retrying."

        private const val DEFAULT_LIST_LIMIT = 100
        private const val DEFAULT_SEARCH_LIMIT = 25
        private const val DEFAULT_PRIORITY = "Low"
    }
}
