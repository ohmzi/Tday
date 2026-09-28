package com.ohmz.tday.compose.feature.widget

abstract class BaseTodayTasksWidgetReceiver : TaskWidgetReceiver() {
    override val kind: WidgetInstanceKind get() = WidgetInstanceKind.TODAY
}

class TodayTasksWidgetSmallReceiver : BaseTodayTasksWidgetReceiver()

class TodayTasksWidgetReceiver : BaseTodayTasksWidgetReceiver()

class TodayTasksWidgetLargeReceiver : BaseTodayTasksWidgetReceiver()
