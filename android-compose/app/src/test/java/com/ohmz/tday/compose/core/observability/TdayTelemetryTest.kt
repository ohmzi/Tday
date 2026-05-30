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
    fun `clamps trace sample rates`() {
        assertEquals(0.25, TdayTelemetry.traceSampleRate("0.25", 1.0), 0.0)
        assertEquals(1.0, TdayTelemetry.traceSampleRate("5", 0.2), 0.0)
        assertEquals(0.2, TdayTelemetry.traceSampleRate("nope", 0.2), 0.0)
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
