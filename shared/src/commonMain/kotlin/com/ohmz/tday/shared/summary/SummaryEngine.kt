package com.ohmz.tday.shared.summary

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Single deterministic task-summary engine shared by the backend and the native
 * app (and mirrored, via the backend, to the web). It is a faithful Kotlin port of
 * the original web implementation (`tday-web/src/lib/todoSummary.ts`): urgency
 * banding, day-grouping, time-of-day windows and natural "Start with …, Next up …"
 * phrasing — fully localized through [SummaryStringBundles].
 *
 * Given the same inputs (tasks, scope, now, timezone, locale) every platform
 * produces a byte-identical summary.
 */
object SummaryEngine {

    private const val DUE_WINDOW_DAY_RANGE = 3
    private val PRIORITY_ALIASES = setOf("medium", "high", "important", "urgent")

    /** Public entry point. Returns the localized summary sentence(s). */
    fun summarize(
        tasks: List<SummaryTaskInput>,
        scope: SummaryScope,
        nowEpochMs: Long,
        timeZoneId: String,
        locale: String? = null,
        listId: String? = null,
    ): String {
        val strings = SummaryStringBundles.forLocale(locale)
        val zone = runCatching { TimeZone.of(timeZoneId) }.getOrDefault(TimeZone.UTC)
        val now = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(zone)
        val normalizedListId = listId?.trim()?.takeIf { it.isNotEmpty() }

        // The weekly retrospective is a different shape from the forward-looking summary:
        // it reads the completed history rather than pending tasks.
        if (scope == SummaryScope.WEEK) {
            return buildWeekReview(tasks, nowEpochMs, now, zone, strings)
        }

        val scoped = tasks
            .asSequence()
            .filterNot { it.completed }
            .filter { matchesScope(it, scope, normalizedListId, now, zone) }
            .toList()

        if (scoped.isEmpty()) return strings.t("clearForNow")

        val candidates = buildCandidates(scoped, now, zone, strings)
        val start = candidates.first()
        val then = candidates.drop(1)
        val overdueCount = scoped.count { it.dueEpochMs != null && it.dueEpochMs < nowEpochMs }
        return buildReadableTaskSummary(start, then, overdueCount, strings)
    }

    // ---- week in review (retrospective over the completed history) ----

    private const val WEEK_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    private fun buildWeekReview(
        tasks: List<SummaryTaskInput>,
        nowEpochMs: Long,
        now: LocalDateTime,
        zone: TimeZone,
        s: SummaryStrings,
    ): String {
        val windowStart = nowEpochMs - WEEK_WINDOW_MS
        val cleared = tasks.filter { task ->
            val completedAt = task.completedAtEpochMs
            task.completed && completedAt != null && completedAt in windowStart..nowEpochMs
        }
        if (cleared.isEmpty()) return s.t("weekNone")

        // DayOfWeek.ordinal is 0=Monday … 6=Sunday, matching weekdaysShort order.
        val byWeekday = cleared.groupBy {
            Instant.fromEpochMilliseconds(it.completedAtEpochMs!!).toLocalDateTime(zone).dayOfWeek.ordinal
        }
        val busiest = byWeekday.entries.maxByOrNull { it.value.size }
        // Oldest cleared: the one that was due earliest, else completed earliest.
        val oldest = cleared.filter { it.dueEpochMs != null }.minByOrNull { it.dueEpochMs!! }
            ?: cleared.minByOrNull { it.completedAtEpochMs!! }

        val sentences = mutableListOf<String>()
        sentences += s.t("weekCleared", mapOf("count" to cleared.size.toString()))
        if (busiest != null && s.weekdaysShort.size == 7) {
            val dayName = s.weekdaysShort[busiest.key]
            sentences += s.t("weekBusiest", mapOf("day" to dayName, "count" to busiest.value.size.toString()))
        }
        if (oldest != null) {
            sentences += s.t("weekOldest", mapOf("title" to compactTitle(oldest.title, s)))
        }
        return sentences.joinToString(" ")
    }

    // ---- scope filtering (mirrors backend buildSummaryTasks filter) ----

    private fun matchesScope(
        task: SummaryTaskInput,
        scope: SummaryScope,
        normalizedListId: String?,
        now: LocalDateTime,
        zone: TimeZone,
    ): Boolean {
        val delta = task.dueEpochMs?.let { dayDelta(it, now, zone) }
        return when (scope) {
            SummaryScope.TODAY -> delta == 0
            SummaryScope.OVERDUE -> task.dueEpochMs != null && task.dueEpochMs < now.toEpochMs(zone)
            SummaryScope.SCHEDULED -> task.dueEpochMs != null && task.dueEpochMs >= now.toEpochMs(
                zone
            )

            SummaryScope.ALL -> true
            SummaryScope.PRIORITY -> isPriorityTask(task.priority)
            SummaryScope.LIST -> normalizedListId != null && task.listId == normalizedListId
            SummaryScope.FLOATER -> true
            // WEEK is handled by buildWeekReview before scope filtering runs.
            SummaryScope.WEEK -> false
        }
    }

    // ---- ranking ----

    private data class Candidate(
        val title: String,
        val dueLabel: String,
        val dueEpochMs: Long?,
        val dueDayDelta: Int,
        val dueDayKey: String,
        val dueDayTarget: String,
        val dueWindowPhrase: String,
        val isOverdue: Boolean,
    )

    private fun buildCandidates(
        tasks: List<SummaryTaskInput>,
        now: LocalDateTime,
        zone: TimeZone,
        strings: SummaryStrings,
    ): List<Candidate> {
        val nowMs = now.toEpochMs(zone)
        val ranked = tasks.sortedWith(
            compareBy<SummaryTaskInput> {
                urgencyBand(it.dueEpochMs?.let { ms ->
                    dayDelta(
                        ms,
                        now,
                        zone
                    )
                })
            }
                .thenBy { it.dueEpochMs?.let { ms -> dayDelta(ms, now, zone) } ?: Int.MAX_VALUE }
                .thenByDescending { priorityRank(it.priority) }
                .thenBy { it.dueEpochMs ?: Long.MAX_VALUE },
        )
        return ranked.map { task ->
            val descriptor = buildDueDescriptor(task.dueEpochMs, now, zone, strings)
            Candidate(
                title = compactTitle(task.title, strings),
                dueLabel = descriptor.dueLabel,
                dueEpochMs = task.dueEpochMs,
                dueDayDelta = task.dueEpochMs?.let { dayDelta(it, now, zone) } ?: Int.MAX_VALUE,
                dueDayKey = descriptor.dueDayKey,
                dueDayTarget = descriptor.dueDayTarget,
                dueWindowPhrase = descriptor.dueWindowPhrase,
                isOverdue = task.dueEpochMs != null && task.dueEpochMs < nowMs,
            )
        }
    }

    // ---- rendering (mirrors buildReadableTaskSummary et al.) ----

    private fun buildReadableTaskSummary(
        start: Candidate,
        then: List<Candidate>,
        overdueCount: Int,
        s: SummaryStrings,
    ): String {
        val sentences = mutableListOf<String>()
        sentences += s.t("startWith", mapOf("task" to taskPhrase(start, s)))
        if (then.isNotEmpty()) {
            sentences += s.t("nextUp", mapOf("tasks" to buildGroupedThenPhrase(then, s)))
        }
        if (overdueCount > 0) {
            sentences += s.t("overdueCatchUp", mapOf("count" to overdueCount.toString()))
        }
        return sentences.joinToString(" ")
    }

    private fun taskPhrase(task: Candidate, s: SummaryStrings): String {
        val isPast = task.isOverdue || task.dueDayDelta < 0
        return s.t(
            if (isPast) "taskPhrasePast" else "taskPhrasePresent",
            mapOf("title" to task.title, "due" to task.dueLabel),
        )
    }

    private fun buildGroupedThenPhrase(then: List<Candidate>, s: SummaryStrings): String {
        if (then.isEmpty()) return ""
        if (then.size == 1) return taskPhrase(then.first(), s)

        val dayGroups = mutableListOf<MutableList<Candidate>>()
        var lastKey: String? = null
        for (task in then) {
            val key = task.dueDayKey
            if (lastKey == key && dayGroups.isNotEmpty()) {
                dayGroups.last().add(task)
            } else {
                dayGroups.add(mutableListOf(task))
                lastKey = key
            }
        }

        val groupPhrases = dayGroups.map { buildDayGroupedPhrase(it, s) }
        if (groupPhrases.size == 1) return groupPhrases.first()

        val sep = s.t("thenSeparator")
        return groupPhrases.dropLast(1).joinToString(sep) + sep + groupPhrases.last()
    }

    private fun buildDayGroupedPhrase(tasks: List<Candidate>, s: SummaryStrings): String {
        if (tasks.isEmpty()) return ""
        if (tasks.size == 1) return taskPhrase(tasks.first(), s)

        val windowGroups = mutableListOf<Pair<String, MutableList<String>>>()
        var lastPhrase: String? = null
        for (task in tasks) {
            val phrase = task.dueWindowPhrase
            if (lastPhrase == phrase && windowGroups.isNotEmpty()) {
                windowGroups.last().second.add(task.title)
            } else {
                windowGroups.add(phrase to mutableListOf(task.title))
                lastPhrase = phrase
            }
        }

        val windowPhrases = windowGroups.map { (phrase, titles) ->
            val joined = joinTaskTitles(titles, s)
            if (phrase.isNotEmpty()) "$joined $phrase" else joined
        }
        val joinedTaskPhrases = joinTaskTitles(windowPhrases, s)
        val dueTarget = tasks.first().dueDayTarget.ifEmpty { tasks.first().dueLabel }
        val qualifier = if (tasks.size == 2) s.t("qualifierBoth") else s.t("qualifierAll")
        val isPast = tasks.first().isOverdue || tasks.first().dueDayDelta < 0
        return s.t(
            if (isPast) "dayGroupedPast" else "dayGroupedPresent",
            mapOf("tasks" to joinedTaskPhrases, "qualifier" to qualifier, "target" to dueTarget),
        )
    }

    private fun joinTaskTitles(titles: List<String>, s: SummaryStrings): String {
        if (titles.isEmpty()) return ""
        if (titles.size == 1) return titles.first()
        val and = s.t("and")
        if (titles.size == 2) return "${titles[0]} $and ${titles[1]}"
        return titles.dropLast(1).joinToString(", ") + ", $and " + titles.last()
    }

    // ---- due descriptor (mirrors buildDueDescriptor) ----

    private data class DueDescriptor(
        val dueLabel: String,
        val dueDayKey: String,
        val dueDayTarget: String,
        val dueWindowPhrase: String,
    )

    private fun buildDueDescriptor(
        dueEpochMs: Long?,
        now: LocalDateTime,
        zone: TimeZone,
        s: SummaryStrings,
    ): DueDescriptor {
        if (dueEpochMs == null) {
            val anytime = s.t("dueAnytime")
            return DueDescriptor(
                dueLabel = anytime,
                dueDayKey = "anytime",
                dueDayTarget = anytime,
                dueWindowPhrase = ""
            )
        }
        val due = Instant.fromEpochMilliseconds(dueEpochMs).toLocalDateTime(zone)
        val delta = dayDelta(dueEpochMs, now, zone)
        val includeWindow = kotlin.math.abs(delta) <= DUE_WINDOW_DAY_RANGE
        val window = dueWindow(due.hour)
        val neutralWindowPhrase = if (includeWindow) dueWindowPhrase(window, s) else ""
        val dueDayKey = isoDate(due)
        val windowWord = if (window == Window.NIGHT) s.t("night") else s.t("window_${window.key}")

        return when (delta) {
            0 -> DueDescriptor(
                dueLabel = if (window == Window.NIGHT) s.t("dueTonight")
                else s.t("dueTodayWindow", mapOf("window" to neutralWindowPhrase)).trim(),
                dueDayKey = dueDayKey,
                dueDayTarget = s.t("targetToday"),
                dueWindowPhrase = neutralWindowPhrase,
            )

            1 -> DueDescriptor(
                dueLabel = s.t("dueTomorrowWindow", mapOf("window" to windowWord)).trim(),
                dueDayKey = dueDayKey,
                dueDayTarget = s.t("targetTomorrow"),
                dueWindowPhrase = neutralWindowPhrase,
            )

            -1 -> DueDescriptor(
                dueLabel = s.t("dueYesterdayWindow", mapOf("window" to windowWord)).trim(),
                dueDayKey = dueDayKey,
                dueDayTarget = s.t("targetYesterday"),
                dueWindowPhrase = neutralWindowPhrase,
            )

            else -> {
                val dayLabel = formatMonthDay(due, now, s)
                val dueDayTarget = s.t("targetOnDate", mapOf("date" to dayLabel))
                DueDescriptor(
                    dueLabel = if (neutralWindowPhrase.isNotEmpty())
                        s.t(
                            "dueOnDateWindow",
                            mapOf("target" to dueDayTarget, "window" to neutralWindowPhrase)
                        ).trim()
                    else s.t("dueOnDate", mapOf("target" to dueDayTarget)),
                    dueDayKey = dueDayKey,
                    dueDayTarget = dueDayTarget,
                    dueWindowPhrase = neutralWindowPhrase,
                )
            }
        }
    }

    private fun formatMonthDay(due: LocalDateTime, now: LocalDateTime, s: SummaryStrings): String {
        val month = s.monthsShort.getOrElse(due.monthNumber - 1) { due.monthNumber.toString() }
        val sameYear = due.year == now.year
        val key = if (sameYear) "dateMonthDay" else "dateMonthDayYear"
        return s.t(
            key,
            mapOf(
                "day" to due.dayOfMonth.toString(),
                "month" to month,
                "year" to due.year.toString()
            )
        )
    }

    // ---- small helpers ----

    private enum class Window(val key: String) { MORNING("morning"), AFTERNOON("afternoon"), NIGHT("night") }

    private fun dueWindow(hour: Int): Window = when {
        hour < 12 -> Window.MORNING
        hour < 18 -> Window.AFTERNOON
        else -> Window.NIGHT
    }

    private fun dueWindowPhrase(window: Window, s: SummaryStrings): String = when (window) {
        Window.MORNING -> s.t("windowMorning")
        Window.AFTERNOON -> s.t("windowAfternoon")
        Window.NIGHT -> s.t("windowNight")
    }

    private fun urgencyBand(dayDelta: Int?): Int = when {
        dayDelta == null -> 6
        dayDelta < 0 -> 0
        dayDelta == 0 -> 1
        dayDelta == 1 -> 2
        dayDelta <= 7 -> 3
        dayDelta <= 30 -> 4
        else -> 5
    }

    private fun priorityRank(priority: String?): Int {
        return when ((priority ?: "Low").trim().lowercase()) {
            "high", "urgent", "important" -> 3
            "medium" -> 2
            else -> 1
        }
    }

    private fun isPriorityTask(priority: String?): Boolean =
        (priority ?: "").trim().lowercase() in PRIORITY_ALIASES

    private fun compactTitle(title: String?, s: SummaryStrings): String {
        val normalized = (title ?: "").replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return s.t("untitledTask")
        if (normalized.length <= 46) return normalized
        return normalized.substring(0, 43).trimEnd() + "..."
    }

    /** Calendar-day difference between [dueEpochMs] and [now], both zoned. */
    private fun dayDelta(dueEpochMs: Long, now: LocalDateTime, zone: TimeZone): Int {
        val due = Instant.fromEpochMilliseconds(dueEpochMs).toLocalDateTime(zone)
        return due.date.toEpochDays() - now.date.toEpochDays()
    }

    private fun LocalDateTime.toEpochMs(zone: TimeZone): Long =
        this.toInstant(zone).toEpochMilliseconds()

    private fun isoDate(dt: LocalDateTime): String {
        val m = dt.monthNumber.toString().padStart(2, '0')
        val d = dt.dayOfMonth.toString().padStart(2, '0')
        return "${dt.year}-$m-$d"
    }
}
