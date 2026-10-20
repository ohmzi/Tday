package com.ohmz.tday.observability

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import io.sentry.Sentry
import io.sentry.logback.SentryAppender
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The shipped logback.xml, loaded into a private context so the test run's own logging is untouched. */
class LogbackSentryAppenderTest {
    @AfterEach
    fun cleanUp() {
        System.clearProperty("sentry.dsn")
        Sentry.close()
    }

    @Test
    fun `only error lines become breadcrumbs`() {
        val appender = loadAppender()

        // INFO and WARN lines carry user ids, paths and push endpoints; the ERROR lines already
        // become events of their own.
        assertEquals(Level.ERROR, appender.minimumBreadcrumbLevel)
        assertEquals(Level.ERROR, appender.minimumEventLevel)
    }

    @Test
    fun `starting the appender does not initialise the sdk behind the gate`() {
        // The appender initialises Sentry on its own from SENTRY_DSN when it starts, which runs
        // before main() builds the gated options. Anything that client sent would bypass the
        // consent gate, so the configuration has to keep the appender from starting a client.
        System.setProperty("sentry.dsn", "https://publickey@o1.ingest.sentry.io/1")

        loadAppender()

        assertFalse(Sentry.isEnabled())
    }

    private fun loadAppender(): SentryAppender {
        val context = LoggerContext()
        JoranConfigurator().apply {
            this.context = context
            doConfigure(checkNotNull(javaClass.classLoader.getResource("logback.xml")))
        }
        return context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender("SENTRY") as SentryAppender
    }
}
