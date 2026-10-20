package com.ohmz.tday.compose.core.observability

import org.junit.Assert.assertEquals
import org.junit.Test

// On-device verification that TdayTelemetry's patterns compile where they actually run.
//
// android-compose/app/src/test runs on the JVM, whose java.util.regex accepts patterns Android's
// ICU-backed engine rejects. A bare `}` after an escaped `{` is one of them: the JVM takes
// `^\{name}$` and ICU raises PatternSyntaxException at class-init time, which took the whole app
// down on launch while `:app:testDebugUnitTest` stayed green. Touching TdayTelemetry here runs that
// same static initializer on a device, so this test is the guard.
class TdayTelemetryPatternTest {
    @Test
    fun routeTemplateSegmentsResolveOnTheDevice() {
        assertEquals(
            "/todos/list/:listId/:listName",
            TdayTelemetry.navigationTemplate("todos/list/{listId}/{listName}"),
        )
        assertEquals("/:listId", TdayTelemetry.navigationTemplate("{listId}"))
        assertEquals("/help-guide", TdayTelemetry.navigationTemplate("help-guide?topic={topic}"))
    }

    @Test
    fun segmentsThatAreNeitherRouteNameNorPlaceholderAreNotTrusted() {
        assertEquals("/todos/list/:value", TdayTelemetry.navigationTemplate("todos/list/9d2f 4c"))
        assertEquals("/settings/:value", TdayTelemetry.navigationTemplate("settings/{2fast}"))
    }

    @Test
    fun scanningKeepsRunningWithTheDeviceRegexEngine() {
        assertEquals("/api/list/:id", TdayTelemetry.sanitizePath("/api/list/list-123?token=secret"))
        assertEquals("redacted", TdayTelemetry.safeLabel("alex@example.com"))
    }
}
