package com.ohmz.tday.compose.feature.widget

abstract class BaseFloaterTasksWidgetReceiver : TaskWidgetReceiver() {
    override val kind: WidgetInstanceKind get() = WidgetInstanceKind.FLOATER
}

class FloaterTasksWidgetSmallReceiver : BaseFloaterTasksWidgetReceiver()

class FloaterTasksWidgetReceiver : BaseFloaterTasksWidgetReceiver()

class FloaterTasksWidgetLargeReceiver : BaseFloaterTasksWidgetReceiver()
