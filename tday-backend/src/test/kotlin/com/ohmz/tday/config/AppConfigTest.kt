package com.ohmz.tday.config

import kotlin.test.Test
import kotlin.test.assertEquals

class AppConfigTest {
    @Test
    fun `production samples one request in ten and other environments sample everything`() {
        assertEquals(0.1, AppConfig.defaultSentryTracesSampleRate(isProduction = true))
        assertEquals(1.0, AppConfig.defaultSentryTracesSampleRate(isProduction = false))
    }
}
