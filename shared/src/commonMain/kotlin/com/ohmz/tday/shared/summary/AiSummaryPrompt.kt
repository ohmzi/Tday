package com.ohmz.tday.shared.summary

import com.ohmz.tday.shared.floater.FloaterResting
import com.ohmz.tday.shared.floater.FloaterRestingTier
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The instructions handed to the model behind the AI summary.
 *
 * This lives beside [SummaryEngine] rather than inside the backend route that calls it because
 * the two are one piece of copy policy, not two features. What the deterministic engine refuses
 * to say, the prompt has to refuse to ask for: while the prompt still said "Mention urgency" for
 * a view whose tasks have no deadline, any deployment running the `ai` profile rendered the AI
 * sentence and demoted the rewritten deterministic copy to a fallback nobody saw. Co-locating
 * them also puts the prompt under `:shared:jvmTest`, which every client's CI already runs.
 *
 * The backend is the only caller — web local mode and offline Android never reach a model — but
 * the backend serves web, iOS and online Android, so this text is what most users actually read.
 */
object AiSummaryPrompt {

    /** How many rows are described to the model at most. Beyond this the prompt stops growing. */
    const val MAX_TASKS = 40

    /** How long one title may be inside the prompt. */
    const val MAX_TITLE_LENGTH = 96

    /**
     * True when this set is the undated ("Anytime") kind.
     *
     * Keyed off the DATA, exactly as [SummaryEngine] keys its own undated branch, and for the
     * same reason: the caller's scope enum has been wrong before. Two switches reading two
     * different signals is how the deterministic and AI halves drift apart.
     */
    fun isUndated(tasks: List<SummaryTaskInput>): Boolean =
        tasks.isNotEmpty() && tasks.all { it.dueEpochMs == null }

    fun build(
        tasks: List<SummaryTaskInput>,
        scope: SummaryScope,
        nowEpochMs: Long,
        timeZoneId: String,
        locale: String? = null,
    ): String {
        val language = languageLine(locale)
        return if (isUndated(tasks)) {
            undatedPrompt(tasks, nowEpochMs, language)
        } else {
            datedPrompt(tasks, scope, nowEpochMs, timeZoneId, language)
        }
    }

    /**
     * The undated brief. It differs from the dated one in every clause that could produce a
     * sentence the deterministic engine would refuse:
     *  - no "Mention urgency": there is no deadline to be urgent about;
     *  - no "Task count: N": the rows are on screen, so the count is the redundancy itself, and
     *    a number the model restates is a number it can get wrong;
     *  - "Anytime", the word on the screen, rather than the internal "floater";
     *  - the same job the planner does — shape, pins, dormancy, at most one title.
     */
    private fun undatedPrompt(
        tasks: List<SummaryTaskInput>,
        nowEpochMs: Long,
        language: String,
    ): String {
        val lines = tasks.take(MAX_TASKS).joinToString("\n") { task ->
            val markers = listOfNotNull(
                task.priority?.takeIf { it.isNotBlank() },
                "pinned".takeIf { task.pinned },
                // Dormancy is the one thing about an undated task that changes over time, and it
                // is the signal the list itself only whispers. Same tier the clients fade by.
                "untouched for months".takeIf {
                    FloaterResting.tierFor(task.updatedAtEpochMs, nowEpochMs) == FloaterRestingTier.RESTING
                },
            ).joinToString(", ")
            if (markers.isEmpty()) "- ${boundedTitle(task.title)}" else "- $markers; ${boundedTitle(task.title)}"
        }
        // Assembled line by line rather than as a `trimIndent()` block: `lines` is itself
        // multi-line, and its inner lines carry no indentation, so trimIndent would find a common
        // indent of zero and leave every instruction sitting behind twelve spaces of Kotlin source.
        return listOf(
            "Summarize the Anytime view of a personal task planner.",
            "These tasks have no due date at all. They wait until the person picks one up, so nothing here is late, urgent, overdue or scheduled: never say or imply otherwise, and never mention a date or a time.",
            "Say something the list on screen does not already say: how far the pile has grown, what the person pinned, what has sat untouched for months, whether one thing stands out.",
            "Return 1 or 2 short sentences, under 200 characters in total.",
            "Name at most one task, and only if it appears below. State no counts and write no digits. Do not use markdown. Do not invent tasks or facts.",
            language,
            "Tasks:",
            lines,
        ).joinToString("\n")
    }

    private fun datedPrompt(
        tasks: List<SummaryTaskInput>,
        scope: SummaryScope,
        nowEpochMs: Long,
        timeZoneId: String,
        language: String,
    ): String {
        val zone = runCatching { TimeZone.of(timeZoneId) }.getOrDefault(TimeZone.UTC)
        val now = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(zone)
        val lines = tasks.take(MAX_TASKS).joinToString("\n") { task ->
            val dueText = task.dueEpochMs?.let { dueMs ->
                val due = Instant.fromEpochMilliseconds(dueMs).toLocalDateTime(zone)
                when {
                    dueMs < nowEpochMs -> "overdue"
                    due.date == now.date -> "due today"
                    else -> "due ${due.date}"
                }
            } ?: "anytime"
            val markers = listOfNotNull(
                task.priority?.takeIf { it.isNotBlank() },
                dueText,
                "pinned".takeIf { task.pinned },
                "recurring".takeIf { task.recurring },
            ).joinToString(", ")
            "- ${task.kind}; $markers; ${boundedTitle(task.title)}"
        }
        return listOf(
            "Summarize this ${scope.responseMode} task view for a personal task planner.",
            "Return 1-2 short, useful sentences. Mention urgency, priority, and where to start when helpful.",
            "Do not use markdown. Do not invent tasks.",
            language,
            "Task count: ${tasks.size}",
            "Tasks:",
            lines,
        ).joinToString("\n")
    }

    /**
     * The locale line. Until now the prompt carried no locale at all, so a French user with the
     * `ai` profile enabled got an English summary sitting above a French screen — the
     * deterministic engine has been translating into ten languages the whole time.
     */
    private fun languageLine(locale: String?): String {
        val tag = locale?.trim()?.takeIf { it.isNotEmpty() } ?: return "Write in English."
        return "Write the answer in the language of this IETF locale tag: $tag."
    }

    private fun boundedTitle(title: String): String {
        val normalized = title.trim().replace(Regex("\\s+"), " ")
        return if (normalized.length <= MAX_TITLE_LENGTH) {
            normalized
        } else {
            normalized.take(MAX_TITLE_LENGTH - 3).trimEnd() + "..."
        }
    }
}

/**
 * The gate an AI answer has to pass before it replaces the deterministic summary.
 *
 * A prompt is a request, not a constraint: a model can still return a count that is wrong, a
 * markdown bullet list, or half a page. Everything checkable without a second model call is
 * checked here, and anything that fails falls back to [SummaryEngine] — which is the same copy,
 * just written by the planner instead.
 *
 * What it deliberately does NOT claim to catch: an invented TITLE. Deciding that "Call the
 * plumber" was not in a set of forty is a string-similarity problem across ten languages, and a
 * check that is wrong either way is worse than none — a false positive silently disables the AI
 * summary, a false negative gives false assurance. The prompt forbids it and the ceiling on
 * length keeps the blast radius to one clause; the honest statement is that titles rest on the
 * model's obedience while counts do not.
 */
object AiSummaryGuard {

    /** Roughly 1.5x the longest sentence the deterministic engine can emit. */
    const val MAX_LENGTH = 320

    /** At most two sentences were asked for; three lines is already a list, not a summary. */
    private const val MAX_LINES = 3

    private val digits = Regex("\\d+")
    private val listMarker = Regex("^([-*•]|#{1,6}\\s|\\d+[.)])")

    /**
     * Returns [candidate] when it is safe to show, or null to fall back to the deterministic
     * engine. [tasks] is the exact set the prompt described, so every fact the guard checks
     * against is a fact the summary was allowed to know.
     */
    fun vet(candidate: String?, tasks: List<SummaryTaskInput>, nowEpochMs: Long): String? {
        val text = candidate?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (text.length > MAX_LENGTH) return null
        if (text.contains("```") || text.contains("**")) return null
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size > MAX_LINES) return null
        if (lines.any { listMarker.containsMatchIn(it) }) return null

        // The numeric rule applies to undated sets only. A dated summary may legitimately print a
        // date ("due Jun 12"), so there every digit would have to be re-derived from the calendar
        // to be judged — a second engine. An undated set has no dates by definition, so the only
        // honest number in one is a count of its own rows: the very invariant
        // FloaterSummaryTest.everyNumberInTheSummaryIsDerivableFromTheInput holds the
        // deterministic path to. The AI path is now held to it too.
        if (!AiSummaryPrompt.isUndated(tasks)) return text

        // Digits that came from a title the model was given are the input's own, not a claim.
        var scrubbed = text
        for (task in tasks) {
            val normalized = task.title.trim().replace(Regex("\\s+"), " ")
            if (normalized.isNotEmpty()) scrubbed = scrubbed.replace(normalized, " ")
        }
        val derivable = derivableCounts(tasks, nowEpochMs)
        for (match in digits.findAll(scrubbed)) {
            if (match.value.toLongOrNull() !in derivable) return null
        }
        return text
    }

    /** The only counts an undated summary can state without the set being re-read to check it. */
    private fun derivableCounts(tasks: List<SummaryTaskInput>, nowEpochMs: Long): Set<Long> = setOf(
        tasks.size.toLong(),
        tasks.count { it.pinned }.toLong(),
        tasks.count {
            FloaterResting.tierFor(it.updatedAtEpochMs, nowEpochMs) == FloaterRestingTier.RESTING
        }.toLong(),
        tasks.count { priorityRankOf(it.priority) == HIGH_PRIORITY_RANK }.toLong(),
    )
}
