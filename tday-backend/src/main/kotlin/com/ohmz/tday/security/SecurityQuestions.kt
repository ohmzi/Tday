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
    )

    val VALID_IDS: Set<Int> = ALL.map { it.id }.toSet()

    private val byId: Map<Int, SecurityQuestion> = ALL.associateBy { it.id }

    /** Every distinct unordered pair of question ids — used to derive a stable decoy pair. */
    private val PAIRS: List<List<Int>> = listOf(listOf(1, 2), listOf(1, 3), listOf(2, 3))

    fun questionsFor(ids: List<Int>): List<SecurityQuestion> = ids.mapNotNull { byId[it] }

    /** Answers are matched case- and surrounding-whitespace-insensitively. */
    fun normalizeAnswer(raw: String): String = raw.trim().lowercase()

    /**
     * Validates a 2-of-3 selection. Returns an error message, or null if valid:
     * exactly two distinct, known question ids, each with a non-blank answer.
     */
    fun validateSelection(answers: List<SecurityAnswerInput>?): String? {
        if (answers == null || answers.size != 2) return "choose exactly two security questions"
        val ids = answers.map { it.questionId }
        if (ids.toSet().size != 2) return "security questions must be different"
        if (!ids.all { it in VALID_IDS }) return "invalid security question"
        if (answers.any { it.answer.trim().isEmpty() }) return "security answers cannot be empty"
        return null
    }

    /**
     * A deterministic 2-question pair derived from a keyed hash of the username, so
     * an unknown or not-yet-configured account returns a stable, realistic challenge
     * indistinguishable from a real user's. Defends against username enumeration.
     */
    fun decoyPair(keyedHashHex: String): List<Int> {
        val seed = keyedHashHex.takeLast(8).toLongOrNull(16) ?: 0L
        val index = (seed % PAIRS.size).toInt().let { if (it < 0) it + PAIRS.size else it }
        return PAIRS[index]
    }
}
