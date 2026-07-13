package com.ohmz.tday.compose.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import dagger.hilt.android.EntryPointAccessors

/** Widget row parameter: the cached record id of the tapped task. */
internal val WidgetTaskIdKey = ActionParameters.Key<String>("widget_task_id")

internal fun completeTodayTaskAction(taskId: String) =
    actionRunCallback<CompleteTodayTaskAction>(actionParametersOf(WidgetTaskIdKey to taskId))

internal fun completeFloaterTaskAction(taskId: String) =
    actionRunCallback<CompleteFloaterTaskAction>(actionParametersOf(WidgetTaskIdKey to taskId))

/** Inline completion from the Today widgets (widgets v2). */
class CompleteTodayTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val taskId = parameters[WidgetTaskIdKey] ?: return
        widgetEntryPoint(context).widgetCompleteTaskSubmitter().completeTodayTask(taskId)
    }
}

/** Inline completion from the Floater widgets (widgets v2). */
class CompleteFloaterTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val taskId = parameters[WidgetTaskIdKey] ?: return
        widgetEntryPoint(context).widgetCompleteTaskSubmitter().completeFloaterTask(taskId)
    }
}

private fun widgetEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
