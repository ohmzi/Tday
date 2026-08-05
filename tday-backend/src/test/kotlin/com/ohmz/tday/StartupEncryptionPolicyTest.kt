package com.ohmz.tday

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Field encryption at rest is OPT-IN, and these tests exist to keep it that way.
 *
 * The polarity matters more than it looks. An earlier version refused to boot whenever a key was
 * missing; on a self-hosted box that bought almost nothing (whoever can read the database already
 * has the host, and the key sits in the same .env.docker) while turning a forgotten variable into a
 * crash loop under `restart: always`. So the default must boot.
 *
 * This control's failure modes are both invisible without tests: too strict and a deploy takes the
 * server down, too lax and REQUIRE_ENCRYPTION_AT_REST silently means nothing.
 */
class StartupEncryptionPolicyTest {

    @Test
    fun `production without a key still boots by default`() {
        // The regression that matters most: a missing key must never be able to strand the owner.
        assertEquals(
            StartupEncryptionVerdict.ProceedWithPlaintextNotice,
            startupEncryptionVerdict(
                isProduction = true,
                encryptionConfigured = false,
                requireEncryptionAtRest = false,
            ),
        )
    }

    @Test
    fun `production with a key boots normally`() {
        assertEquals(
            StartupEncryptionVerdict.Ok,
            startupEncryptionVerdict(
                isProduction = true,
                encryptionConfigured = true,
                requireEncryptionAtRest = false,
            ),
        )
    }

    @Test
    fun `opting in makes a missing key a hard startup failure`() {
        // The opt-in has to actually bite, otherwise it is decoration.
        assertEquals(
            StartupEncryptionVerdict.RefuseBoot,
            startupEncryptionVerdict(
                isProduction = true,
                encryptionConfigured = false,
                requireEncryptionAtRest = true,
            ),
        )
    }

    @Test
    fun `opting in with a key present is simply fine`() {
        assertEquals(
            StartupEncryptionVerdict.Ok,
            startupEncryptionVerdict(
                isProduction = true,
                encryptionConfigured = true,
                requireEncryptionAtRest = true,
            ),
        )
    }

    @Test
    fun `non-production is never blocked, whatever the configuration`() {
        // Local development and CI must keep working with no key set. If this regresses, every
        // contributor's first run fails.
        for (configured in listOf(true, false)) {
            for (required in listOf(true, false)) {
                assertEquals(
                    StartupEncryptionVerdict.NotApplicable,
                    startupEncryptionVerdict(
                        isProduction = false,
                        encryptionConfigured = configured,
                        requireEncryptionAtRest = required,
                    ),
                    "non-production must not be blocked (configured=$configured, required=$required)",
                )
            }
        }
    }

    @Test
    fun `boot is refused in exactly one configuration and no other`() {
        // Enumerating the whole truth table is the cheapest guard against someone "simplifying"
        // this back into fail-closed-by-default later.
        val refusing = buildList {
            for (production in listOf(true, false)) {
                for (configured in listOf(true, false)) {
                    for (required in listOf(true, false)) {
                        if (startupEncryptionVerdict(production, configured, required) == StartupEncryptionVerdict.RefuseBoot) {
                            add(Triple(production, configured, required))
                        }
                    }
                }
            }
        }
        assertEquals(listOf(Triple(true, false, true)), refusing)
    }
}
