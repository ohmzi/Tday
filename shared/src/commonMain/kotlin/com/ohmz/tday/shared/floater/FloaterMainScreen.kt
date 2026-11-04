package com.ohmz.tday.shared.floater

/**
 * Whether a floater appears on the Floater main-screen surfaces: the feed, the all-floaters widget
 * and the no-list summary. A list turns this off with `showOnMainScreen`.
 *
 * Takes the hidden ids rather than the lists, so a task with no list, or one whose list has not
 * loaded, reads as shown: a task must never vanish because metadata is missing.
 *
 * Mirrored by `tday-web/src/lib/floater/mainScreen.ts` and
 * `ios-swiftUI/Tday/Core/Model/FloaterMainScreen.swift`, which cannot link this file; the guardrail
 * in `tday-web/tests/guardrails/floater-main-screen-parity.test.ts` keeps them in step.
 */
object FloaterMainScreen {
    fun isShown(listId: String?, hiddenListIds: Set<String>): Boolean =
        listId == null || listId !in hiddenListIds
}
