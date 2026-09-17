package com.ohmz.tday.compose.core.navigation

/**
 * Which of the completion history's two tabs an arrival opens on.
 *
 * The vocabulary is WEB'S, and deliberately not a third spelling of the same
 * idea: the web Completed screen drives its `?scope=` query with
 * `type CompletedScope = "tasks" | "floater"` (`CompletedContainer.tsx`), and
 * `tday://completed?scope=floater` has to mean the same thing on this client as
 * `/app/completed?scope=floater` means there. iOS says the same two things with
 * `HomeTileOrigin.scheduledBoard` / `.floaterFeed`, so all three clients answer
 * one question in one vocabulary rather than three.
 *
 * The TAB LABEL and the wire value disagree on purpose, and on web too: the
 * first tab is labelled "Scheduled" (it mirrors the dock tab it copies) while
 * its id stays `tasks`. That mismatch is web's, and renaming the wire value
 * here would break the one thing the deep links agree on.
 *
 * [fromWire] is total rather than nullable: anything that is not `floater` — an
 * absent argument, an empty string, `tasks`, or a value nothing writes — opens
 * the first tab. That is the rule the web screen states for itself ("anything
 * else opens the first tab"), and it is the safe polarity: a deep link that
 * names a tab the app has never heard of lands on the history instead of on
 * nothing.
 */
enum class CompletedScope(val wire: String) {
    /** Completed scheduled tasks. Web's id for this tab is `tasks`; its label is "Scheduled". */
    Tasks("tasks"),

    /** Completed floaters. */
    Floater("floater"),
    ;

    companion object {
        /** The tab a route argument names, or [Tasks] for anything that names no tab. */
        fun fromWire(value: String?): CompletedScope =
            if (value.equals(Floater.wire, ignoreCase = true)) Floater else Tasks
    }
}
