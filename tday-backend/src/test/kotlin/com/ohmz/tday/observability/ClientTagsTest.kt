package com.ohmz.tday.observability

import kotlin.test.Test
import kotlin.test.assertEquals

class ClientTagsTest {
    @Test
    fun `tags the platform and version of a native client`() {
        assertEquals(
            mapOf("client.platform" to "ios", "client.version" to "0.8.0"),
            clientTags(clientHeader = "ios", versionHeader = "0.8.0"),
        )
        assertEquals(
            mapOf("client.platform" to "android", "client.version" to "12.34.567"),
            clientTags(clientHeader = "android-compose", versionHeader = "12.34.567"),
        )
    }

    @Test
    fun `accepts the web client and tolerates case and padding`() {
        assertEquals(
            mapOf("client.platform" to "web"),
            clientTags(clientHeader = " Web ", versionHeader = null),
        )
    }

    @Test
    fun `a missing header just leaves its tag out`() {
        assertEquals(mapOf("client.version" to "1.2.3"), clientTags(null, " 1.2.3 "))
        assertEquals(emptyMap(), clientTags(null, null))
    }

    @Test
    fun `values outside the enum and semver shapes are never tagged`() {
        // A header is attacker-controlled free text. Tagging it verbatim would let any caller
        // put arbitrary strings, including other people's identifiers, into the error tracker.
        assertEquals(emptyMap(), clientTags("<script>alert(1)</script>", "1.0.0 alex@example.com"))
        assertEquals(emptyMap(), clientTags("curl", "latest"))
        assertEquals(emptyMap(), clientTags("", ""))
        assertEquals(mapOf("client.platform" to "ios"), clientTags("ios", "1.2.3.4"))
        assertEquals(emptyMap(), clientTags("ios".repeat(100), "1".repeat(1000)))
    }
}
