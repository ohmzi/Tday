package com.ohmz.tday.compose.feature.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.ohmz.tday.compose.R
import org.junit.Assert.assertEquals
import org.junit.Test

// On-device verification of the host behaviour behind "resizing the widget duplicates the
// watermark": a host that already shows widget_task re-applies each new render onto the live
// views (RemoteViews.reapply) instead of inflating a fresh tree, so anything an earlier render
// showed stays shown unless the new one hides it. JVM tests can only pin the source contract
// (WidgetReapplyVisibilityTest); this drives the real RemoteViews apply/reapply path.
class TaskWidgetReapplyTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun resizingShowsOnlyTheNewBucketsWatermark() {
        val views = apply(model(TaskWidgetContentState.EMPTY), MEDIUM)
        assertEquals(listOf(R.id.widget_watermark_medium), visibleWatermarks(views))

        reapply(views, model(TaskWidgetContentState.EMPTY), TALL)
        assertEquals(listOf(R.id.widget_watermark_tall), visibleWatermarks(views))

        reapply(views, model(TaskWidgetContentState.EMPTY), COMPACT)
        assertEquals(listOf(R.id.widget_watermark_compact), visibleWatermarks(views))
    }

    @Test
    fun resizingOutOfCompactRestoresTheWideHeader() {
        val views = apply(model(TaskWidgetContentState.TASKS), COMPACT)
        assertEquals(View.GONE, views.findViewById<View>(R.id.widget_header_wide).visibility)
        assertEquals(View.VISIBLE, views.findViewById<View>(R.id.widget_compact_count).visibility)

        reapply(views, model(TaskWidgetContentState.TASKS), MEDIUM)
        assertEquals(View.VISIBLE, views.findViewById<View>(R.id.widget_header_wide).visibility)
        assertEquals(View.GONE, views.findViewById<View>(R.id.widget_compact_count).visibility)
    }

    @Test
    fun emptyingTheListHidesItAndShowsTheMessage() {
        val views = apply(model(TaskWidgetContentState.TASKS), MEDIUM)
        assertEquals(View.VISIBLE, views.findViewById<View>(R.id.widget_list).visibility)
        assertEquals(View.GONE, views.findViewById<View>(R.id.widget_message).visibility)

        reapply(views, model(TaskWidgetContentState.EMPTY), MEDIUM)
        assertEquals(View.GONE, views.findViewById<View>(R.id.widget_list).visibility)
        assertEquals(View.VISIBLE, views.findViewById<View>(R.id.widget_message).visibility)
        assertEquals(View.GONE, views.findViewById<View>(R.id.widget_count).visibility)
    }

    private fun apply(model: TaskWidgetModel, size: DpSize): View =
        TaskWidgetRemoteViews.build(context, WIDGET_ID, model, listOf(size))
            .apply(context, FrameLayout(context))

    private fun reapply(views: View, model: TaskWidgetModel, size: DpSize) {
        TaskWidgetRemoteViews.build(context, WIDGET_ID, model, listOf(size)).reapply(context, views)
    }

    private fun visibleWatermarks(views: View): List<Int> =
        TaskWidgetLayout.entries
            .map(::taskWidgetWatermarkViewId)
            .filter { views.findViewById<View>(it).visibility == View.VISIBLE }

    private fun model(state: TaskWidgetContentState) = TaskWidgetModel(
        title = "Today's Tasks",
        state = state,
        countLabel = "1 due".takeIf { state == TaskWidgetContentState.TASKS },
        setupTitle = "",
        setupMessage = "",
        emptyTitle = "No tasks due today",
        lockedTitle = "",
        lockedMessage = "",
        loadingTitle = "",
        addLabel = "Add",
        items = if (state == TaskWidgetContentState.TASKS) {
            listOf(TaskWidgetListItem.Task(TaskWidgetRow(key = 1L, id = "row", title = "Row", priority = "Low")))
        } else {
            emptyList()
        },
        visuals = todayWidgetVisuals(isDaytime = true),
        openIntent = Intent(Intent.ACTION_MAIN),
        addIntent = Intent(Intent.ACTION_MAIN),
    )

    private companion object {
        const val WIDGET_ID = 1
        val COMPACT = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 160.dp)
        val TALL = DpSize(250.dp, 300.dp)
    }
}
