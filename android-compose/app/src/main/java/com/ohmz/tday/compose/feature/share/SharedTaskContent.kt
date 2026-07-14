package com.ohmz.tday.compose.feature.share

/**
 * What an ACTION_SEND text share prefills in the create-task sheet. Kept as a
 * pure mapping (no Intent) so the subject/text precedence is unit-testable.
 */
internal data class SharedTaskContent(
    val title: String,
    val notes: String?,
)

/**
 * Maps a share's EXTRA_SUBJECT/EXTRA_TEXT pair to sheet prefills. Browsers
 * share links as page-title subject + URL text, so a distinct subject becomes
 * the task title with the text kept as notes; bare text (messages, selected
 * words) is itself the title, where the NLP date parser can pick a due date
 * out of it. Returns null when the share carries nothing usable.
 */
internal fun sharedTaskContent(subject: String?, text: String?): SharedTaskContent? {
    val trimmedSubject = subject?.trim().orEmpty()
    val trimmedText = text?.trim().orEmpty()
    return when {
        trimmedSubject.isNotEmpty() && trimmedText.isNotEmpty() && trimmedSubject != trimmedText ->
            SharedTaskContent(title = trimmedSubject, notes = trimmedText)

        trimmedText.isNotEmpty() ->
            SharedTaskContent(title = trimmedText, notes = null)

        trimmedSubject.isNotEmpty() ->
            SharedTaskContent(title = trimmedSubject, notes = null)

        else -> null
    }
}
