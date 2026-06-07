package com.ohmz.tday.security

import com.ohmz.tday.models.request.SecurityAnswerInput
import kotlinx.serialization.Serializable

@Serializable
data class SecurityQuestion(val id: Int, val text: String)

/**
 * The fixed catalogue of security questions. Only the integer id and a hashed
 * answer are ever persisted (see `user_security_questions`), so the wording here
 * can change without a migration.
 */
object SecurityQuestions {
    val ALL: List<SecurityQuestion> = listOf(
        SecurityQuestion(1, "What was the name of your first pet?"),
        SecurityQuestion(2, "What city were you born in?"),
        SecurityQuestion(3, "What was the make and model of your first car?"),
        SecurityQuestion(4, "What is your mother's maiden name?"),
        SecurityQuestion(5, "What was the name of your first school?"),
        SecurityQuestion(6, "What was the name of your childhood best friend?"),
        SecurityQuestion(7, "What is the name of the street you grew up on?"),
        SecurityQuestion(8, "What was your favourite teacher's name?"),
        SecurityQuestion(9, "What was the name of your first employer?"),
        SecurityQuestion(10, "What is your favourite book?"),
    )

    val VALID_IDS: Set<Int> = ALL.map { it.id }.toSet()

    private val byId: Map<Int, SecurityQuestion> = ALL.associateBy { it.id }

    fun questionsFor(ids: List<Int>): List<SecurityQuestion> = ids.mapNotNull { byId[it] }

    /** Answers are matched case- and surrounding-whitespace-insensitively. */
    fun normalizeAnswer(raw: String): String = raw.trim().lowercase()

    /**
     * Validates a selection of exactly [required] questions. Returns an error message,
     * or null if valid: [required] distinct, known question ids, each with a non-blank
     * answer. Registration/updates use 3; password reset answers 2 of those.
     */
    fun validateSelection(answers: List<SecurityAnswerInput>?, required: Int): String? {
        if (answers == null || answers.size != required) return "choose exactly $required security questions"
        val ids = answers.map { it.questionId }
        if (ids.toSet().size != required) return "security questions must be different"
        if (!ids.all { it in VALID_IDS }) return "invalid security question"
        if (answers.any { it.answer.trim().isEmpty() }) return "security answers cannot be empty"
        return null
    }

    /**
     * A deterministic [count]-question set derived from a keyed hash, so an unknown or
     * not-yet-configured account returns a stable, realistic challenge indistinguishable
     * from a real user's. Defends against username enumeration.
     */
    fun decoyIds(keyedHashHex: String, count: Int): List<Int> {
        return stableOrder(VALID_IDS.toList(), keyedHashHex).take(count).sorted()
    }

    /** Back-compat helper: a stable 2-question decoy challenge. */
    fun decoyPair(keyedHashHex: String): List<Int> = decoyIds(keyedHashHex, 2)

    /**
     * A stable [count]-subset of a known user's [storedIds], derived from the keyed hash
     * so the same questions are shown on every reset attempt (and the subset is always a
     * subset of what's stored, so verification's containsAll check holds). Returns all
     * stored ids when there are at most [count].
     */
    fun stableSubset(storedIds: List<Int>, keyedHashHex: String, count: Int): List<Int> {
        if (storedIds.size <= count) return storedIds.sorted()
        return stableOrder(storedIds, keyedHashHex).take(count).sorted()
    }

    /** Deterministic, seed-dependent ordering of [ids] (a keyed shuffle). */
    private fun stableOrder(ids: List<Int>, keyedHashHex: String): List<Int> {
        val seed = keyedHashHex.takeLast(12).toLongOrNull(16) ?: 0L
        return ids.sortedBy { id -> mix(seed, id) }
    }

    private fun mix(seed: Long, id: Int): Long {
        var x = seed + id.toLong() * 0x9E3779B1L
        x = (x xor (x ushr 16)) * 0x45D9F3BL
        x = (x xor (x ushr 16)) * 0x45D9F3BL
        x = x xor (x ushr 16)
        return x
    }
}
