package com.ohmz.tday.observability

import kotlin.test.Test
import kotlin.test.assertEquals

class TdayObservabilityTest {
    @Test
    fun `sanitizes api identifiers and query strings`() {
        assertEquals(
            "/api/list/:id",
            TdayObservability.sanitizePath("/api/list/list-123?token=secret"),
        )
        assertEquals(
            "GET /api/todo/:id",
            TdayObservability.routeTemplate("get", "/api/todo/cjld2cjxh0000qzrmn831i7rn?email=a@example.com"),
        )
    }

    @Test
    fun `keeps known structural routes readable`() {
        assertEquals("/api/auth/session", TdayObservability.sanitizePath("/api/auth/session"))
        assertEquals("/api/todo/summary", TdayObservability.sanitizePath("/api/todo/summary"))
        assertEquals("/:locale/app/list/:id", TdayObservability.sanitizePath("/:locale/app/list/:id"))
        assertEquals("/ws", TdayObservability.sanitizePath("/ws"))
    }

    @Test
    fun `redacts freeform path segments`() {
        assertEquals(
            "/:locale/app/list/:id/:value",
            TdayObservability.sanitizePath("/en/app/list/abc-123/Groceries"),
        )
        assertEquals(
            "/api/user/:redacted",
            TdayObservability.sanitizePath("https://example.com/api/user/alex@example.com?token=secret"),
        )
        assertEquals("/", TdayObservability.sanitizePath("https://example.com"))
    }

    @Test
    fun `redacts sensitive labels and token shaped values`() {
        assertEquals("redacted", TdayObservability.safeLabel("alex@example.com"))
        assertEquals("redacted", TdayObservability.safeLabel("https://example.com/api/todo/123"))
        assertEquals("id", TdayObservability.safeLabel("cjld2cjxh0000qzrmn831i7rn"))
    }

    @Test
    fun `sanitizes route like data by key and redacts sensitive fields`() {
        assertEquals(
            "/api/list/:id",
            TdayObservability.safeDataValue("route", "https://example.com/api/list/list-123?token=secret"),
        )
        assertEquals(
            "/:locale/app/list/:id",
            TdayObservability.safeDataValue("from", "/en/app/list/list-123"),
        )
        assertEquals("redacted", TdayObservability.safeDataValue("email", "alex@example.com"))
        assertEquals("redacted", TdayObservability.safeDataValue("authorization", "Bearer secret"))
    }
}
