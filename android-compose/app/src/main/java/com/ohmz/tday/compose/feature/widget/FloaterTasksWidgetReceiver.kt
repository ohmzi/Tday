package com.ohmz.tday.compose.feature.widget

// `open`, not `abstract`: it has nothing left to implement — the three subclasses exist only
// because the manifest/AppWidgetManager need a DISTINCT class per size receiver.
open class BaseFloaterTasksWidgetReceiver : TaskWidgetReceiver() {
    override val kind: WidgetInstanceKind get() = WidgetInstanceKind.FLOATER
}

class FloaterTasksWidgetSmallReceiver : BaseFloaterTasksWidgetReceiver()

class FloaterTasksWidgetReceiver : BaseFloaterTasksWidgetReceiver()

class FloaterTasksWidgetLargeReceiver : BaseFloaterTasksWidgetReceiver()
