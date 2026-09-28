package com.ohmz.tday.compose.feature.widget

// `open`, not `abstract`: it has nothing left to implement — the three subclasses exist only
// because the manifest/AppWidgetManager need a DISTINCT class per size receiver.
open class BaseTodayTasksWidgetReceiver : TaskWidgetReceiver() {
    override val kind: WidgetInstanceKind get() = WidgetInstanceKind.TODAY
}

class TodayTasksWidgetSmallReceiver : BaseTodayTasksWidgetReceiver()

class TodayTasksWidgetReceiver : BaseTodayTasksWidgetReceiver()

class TodayTasksWidgetLargeReceiver : BaseTodayTasksWidgetReceiver()
