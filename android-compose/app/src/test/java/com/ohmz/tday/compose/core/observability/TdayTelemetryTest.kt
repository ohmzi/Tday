package com.ohmz.tday.compose.core.observability

import org.junit.Assert.assertEquals
import org.junit.Test

class TdayTelemetryTest {
    @Test
    fun `sanitizes ids and query strings`() {
        assertEquals(
            "/api/list/:id",
            TdayTelemetry.sanitizePath("/api/list/list-123?token=secret"),
        )
        assertEquals(
            "/:locale/app/list/:id/:value",
            TdayTelemetry.sanitizePath("/en/app/list/list-123/Groceries"),
        )
        assertEquals(
            "/:locale/app/list/:id",
            TdayTelemetry.sanitizePath("/:locale/app/list/:id"),
        )
    }

    @Test
    fun `turns a navigation pattern into a path that names the screen and not what is on it`() {
        assertEquals(
            "/todos/list/:listId/:listName",
            TdayTelemetry.navigationTemplate("todos/list/{listId}/{listName}"),
        )
        assertEquals("/help-guide", TdayTelemetry.navigationTemplate("help-guide?topic={topic}"))
        assertEquals("/home", TdayTelemetry.navigationTemplate("home"))
        assertEquals("/", TdayTelemetry.navigationTemplate(""))
    }

    @Test
    fun `a navigation segment that is neither a route name nor a placeholder is not trusted`() {
        assertEquals(
            "/todos/list/:value/:value",
            TdayTelemetry.navigationTemplate("todos/list/9d2f 4c/Weekly shop"),
        )
    }

    @Test
    fun `redacts sensitive labels and token shaped values`() {
        assertEquals("redacted", TdayTelemetry.safeLabel("alex@example.com"))
        assertEquals("redacted", TdayTelemetry.safeLabel("https://example.com/api/todo/123"))
        assertEquals("id", TdayTelemetry.safeLabel("cjld2cjxh0000qzrmn831i7rn"))
    }

    @Test
    fun `sanitizes route like data by key and redacts sensitive fields`() {
        assertEquals(
            "/api/list/:id",
            TdayTelemetry.safeDataValue("route", "https://example.com/api/list/list-123?token=secret"),
        )
        assertEquals(
            "/:locale/app/list/:id",
            TdayTelemetry.safeDataValue("from", "/en/app/list/list-123"),
        )
        assertEquals("redacted", TdayTelemetry.safeDataValue("email", "alex@example.com"))
        assertEquals("redacted", TdayTelemetry.safeDataValue("authorization", "Bearer secret"))
    }
}
