package com.ohmz.tday.compose.core.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationToggleStateTest {

    @Test
    fun `switch shows on only when the OS allows it and the user asked for it`() {
        assertTrue(notificationToggleChecked(os(authorized = true), preferenceEnabled = true))
        assertFalse(notificationToggleChecked(os(authorized = true), preferenceEnabled = false))
        assertFalse(notificationToggleChecked(os(authorized = false), preferenceEnabled = true))
        assertFalse(notificationToggleChecked(os(authorized = false), preferenceEnabled = false))
    }

    @Test
    fun `turning off only ever touches the stored preference`() {
        for (authorized in listOf(true, false)) {
            for (canPrompt in listOf(true, false)) {
                assertEquals(
                    NotificationToggleAction.DisablePreference,
                    notificationToggleAction(requestedOn = false, os = os(authorized, canPrompt)),
                )
            }
        }
    }

    @Test
    fun `turning on with the permission already held just stores the preference`() {
        assertEquals(
            NotificationToggleAction.EnablePreference,
            notificationToggleAction(requestedOn = true, os = os(authorized = true)),
        )
    }

    @Test
    fun `turning on without authorization asks the OS while it will still ask`() {
        assertEquals(
            NotificationToggleAction.RequestOsPermission,
            notificationToggleAction(
                requestedOn = true,
                os = os(authorized = false, canPrompt = true),
            ),
        )
    }

    @Test
    fun `turning on falls through to settings once the OS stops prompting`() {
        assertEquals(
            NotificationToggleAction.OpenOsSettings,
            notificationToggleAction(
                requestedOn = true,
                os = os(authorized = false, canPrompt = false),
            ),
        )
    }

    @Test
    fun `below API 33 the per-app notification switch is the whole story`() {
        assertTrue(
            isNotificationOsAuthorized(
                sdkInt = 32,
                notificationsEnabled = true,
                permissionGranted = false,
                reminderChannelEnabled = true,
            ),
        )
        assertFalse(
            isNotificationOsAuthorized(
                sdkInt = 32,
                notificationsEnabled = false,
                permissionGranted = true,
                reminderChannelEnabled = true,
            ),
        )
    }

    @Test
    fun `from API 33 both the permission and the app switch have to be on`() {
        assertTrue(
            isNotificationOsAuthorized(
                sdkInt = 33,
                notificationsEnabled = true,
                permissionGranted = true,
                reminderChannelEnabled = true,
            ),
        )
        assertFalse(
            isNotificationOsAuthorized(
                sdkInt = 33,
                notificationsEnabled = false,
                permissionGranted = true,
                reminderChannelEnabled = true,
            ),
        )
        assertFalse(
            isNotificationOsAuthorized(
                sdkInt = 33,
                notificationsEnabled = true,
                permissionGranted = false,
                reminderChannelEnabled = true,
            ),
        )
    }

    @Test
    fun `a reminder channel switched off in Settings reads as unauthorized`() {
        // Both app-level bits stay true when only the channel is turned off, which is
        // exactly the state that used to show ON with nothing being delivered.
        assertFalse(
            isNotificationOsAuthorized(
                sdkInt = 33,
                notificationsEnabled = true,
                permissionGranted = true,
                reminderChannelEnabled = false,
            ),
        )
        assertFalse(
            isNotificationOsAuthorized(
                sdkInt = 26,
                notificationsEnabled = true,
                permissionGranted = false,
                reminderChannelEnabled = false,
            ),
        )
    }

    @Test
    fun `nothing to prompt for below API 33 or with the permission held`() {
        assertFalse(
            canPromptForNotificationPermission(
                sdkInt = 32,
                permissionGranted = false,
                promptExhausted = false,
                shouldShowRationale = true,
            ),
        )
        assertFalse(
            canPromptForNotificationPermission(
                sdkInt = 33,
                permissionGranted = true,
                promptExhausted = true,
                shouldShowRationale = true,
            ),
        )
    }

    @Test
    fun `a never-asked install can still be prompted despite a false rationale`() {
        assertTrue(
            canPromptForNotificationPermission(
                sdkInt = 33,
                permissionGranted = false,
                promptExhausted = false,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun `a dialog dismissed without an answer can still be shown again`() {
        // The first-launch ask records that it launched, not that it was answered, so a
        // dismissal must not read as a permanent denial the way a launch record would.
        assertTrue(
            canPromptForNotificationPermission(
                sdkInt = 33,
                permissionGranted = false,
                promptExhausted = false,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun `a rationale still on offer outranks an earlier exhausted prompt`() {
        assertTrue(
            canPromptForNotificationPermission(
                sdkInt = 33,
                permissionGranted = false,
                promptExhausted = true,
                shouldShowRationale = true,
            ),
        )
    }

    @Test
    fun `once Android stops offering the dialog the tap has to go to Settings`() {
        assertFalse(
            canPromptForNotificationPermission(
                sdkInt = 33,
                permissionGranted = false,
                promptExhausted = true,
                shouldShowRationale = false,
            ),
        )
    }

    private fun os(authorized: Boolean, canPrompt: Boolean = false) =
        NotificationOsState(authorized = authorized, canPrompt = canPrompt)
}
