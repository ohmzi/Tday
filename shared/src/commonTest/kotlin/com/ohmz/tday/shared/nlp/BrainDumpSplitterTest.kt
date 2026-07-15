package com.ohmz.tday.shared.nlp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrainDumpSplitterTest {
    @Test
    fun splitsOnNewlines() {
        assertEquals(
            listOf("buy milk", "call mom", "water plants"),
            BrainDumpSplitter.split("buy milk\ncall mom\nwater plants"),
        )
    }

    @Test
    fun stripsBulletsAndNumbers() {
        assertEquals(
            listOf("groceries", "laundry", "email boss"),
            BrainDumpSplitter.split("- groceries\n* laundry\n1. email boss"),
        )
    }

    @Test
    fun splitsOnAndThenAndSemicolons() {
        assertEquals(
            listOf("pack bags", "book taxi", "check in"),
            BrainDumpSplitter.split("pack bags and then book taxi; check in"),
        )
    }

    @Test
    fun trimsDedupesAndDropsBlanks() {
        assertEquals(
            listOf("gym", "read"),
            BrainDumpSplitter.split("  gym  \n\n gym \n Gym\n read \n\n"),
        )
    }

    @Test
    fun capsAtMaxFragments() {
        val many = (1..80).joinToString("\n") { "task $it" }
        assertEquals(BrainDumpSplitter.MAX_FRAGMENTS, BrainDumpSplitter.split(many).size)
    }

    @Test
    fun emptyInputYieldsNothing() {
        assertTrue(BrainDumpSplitter.split("").isEmpty())
        assertTrue(BrainDumpSplitter.split("   \n  ").isEmpty())
    }
}
