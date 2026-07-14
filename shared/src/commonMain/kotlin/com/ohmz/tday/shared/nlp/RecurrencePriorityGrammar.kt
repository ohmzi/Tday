package com.ohmz.tday.shared.nlp

/** Result of deterministic recurrence + priority capture from a task title. */
data class RecurrencePriorityResult(
    val cleanTitle: String,
    val rrule: String?,
    val priority: String?,
)

/**
 * Deterministic, on-device recurrence + priority capture shared by every platform's
 * on-device title parser. Detects an English recurrence phrase ("every day", "weekly",
 * …) → one of the 5 preset RRULEs, and a priority marker ("!" / "!!" / "high|low|medium
 * priority" / a trailing high|low|medium) → the priority field, stripping the matched
 * phrases from the title. Bare priority words are only honoured when trailing or paired
 * with "priority", to avoid false positives like "buy low-fat milk".
 *
 * The RRULE strings are byte-identical to the create sheets' recurrence presets.
 */
object RecurrencePriorityGrammar {
    private data class Rule(val regex: Regex, val rrule: String)

    private val recurrenceRules = listOf(
        // "weekday(s)" is tried before "week", which it contains.
        Rule(Regex("""\b(?:every\s+weekday|weekdays?)\b""", RegexOption.IGNORE_CASE), "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR"),
        Rule(Regex("""\b(?:every\s*day|everyday|daily)\b""", RegexOption.IGNORE_CASE), "RRULE:FREQ=DAILY;INTERVAL=1"),
        Rule(Regex("""\b(?:every\s+week|weekly)\b""", RegexOption.IGNORE_CASE), "RRULE:FREQ=WEEKLY;INTERVAL=1"),
        Rule(Regex("""\b(?:every\s+month|monthly)\b""", RegexOption.IGNORE_CASE), "RRULE:FREQ=MONTHLY;INTERVAL=1"),
        Rule(Regex("""\b(?:every\s+year|yearly|annually)\b""", RegexOption.IGNORE_CASE), "RRULE:FREQ=YEARLY;INTERVAL=1"),
    )

    private val priorityPhrase = Regex("""\b(high|medium|low)\s+priority\b""", RegexOption.IGNORE_CASE)
    private val priorityTrailing = Regex("""\s+(high|medium|low)\s*$""", RegexOption.IGNORE_CASE)

    fun parse(text: String): RecurrencePriorityResult {
        var working = text
        var rrule: String? = null

        for (rule in recurrenceRules) {
            val match = rule.regex.find(working)
            if (match != null) {
                rrule = rule.rrule
                working = working.removeRange(match.range)
                break
            }
        }

        var priority: String? = null
        when {
            working.contains("!!") -> {
                priority = "High"
                working = working.replaceFirst("!!", "")
            }
            working.contains("!") -> {
                priority = "Medium"
                working = working.replaceFirst("!", "")
            }
            else -> {
                val phrase = priorityPhrase.find(working)
                if (phrase != null) {
                    priority = capitalize(phrase.groupValues[1])
                    working = working.removeRange(phrase.range)
                } else {
                    val trailing = priorityTrailing.find(working)
                    if (trailing != null) {
                        priority = capitalize(trailing.groupValues[1])
                        working = working.removeRange(trailing.range)
                    }
                }
            }
        }

        val cleanTitle = working.replace(Regex("""\s{2,}"""), " ").trim()
        return RecurrencePriorityResult(cleanTitle = cleanTitle, rrule = rrule, priority = priority)
    }

    private fun capitalize(word: String): String = when (word.lowercase()) {
        "high" -> "High"
        "low" -> "Low"
        else -> "Medium"
    }
}
