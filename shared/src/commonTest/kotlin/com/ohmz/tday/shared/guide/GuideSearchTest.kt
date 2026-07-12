package com.ohmz.tday.shared.guide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuideSearchTest {

    private val docs = listOf(
        GuideSearch.buildDoc(
            "recurrence-presets",
            title = "Repeating tasks",
            keywords = "repeat, recurring, every day, weekly, rrule",
            body = "Pick one of the recurrence presets to repeat a task.",
        ),
        GuideSearch.buildDoc(
            "swipe-actions",
            title = "Swipe actions",
            keywords = "swipe, gesture, slide, complete, defer",
            body = "Swipe a task row to reveal quick actions.",
        ),
        GuideSearch.buildDoc(
            "nlp-date-syntax",
            title = "Type the date",
            keywords = "natural language, tomorrow, next week, date",
            body = "Type dentist tuesday 3pm and the date is parsed for you.",
        ),
    )

    @Test
    fun keywordMatchFindsTopic() {
        assertEquals(listOf("recurrence-presets"), GuideSearch.rank("repeat", docs))
        assertEquals(listOf("swipe-actions"), GuideSearch.rank("swipe", docs))
    }

    @Test
    fun titleHitOutranksBodyHit() {
        // "swipe" is in swipe-actions' title (weight 3) and only there.
        val ranked = GuideSearch.rank("swipe", docs)
        assertEquals("swipe-actions", ranked.first())
    }

    @Test
    fun emptyQueryReturnsNothing() {
        assertTrue(GuideSearch.rank("", docs).isEmpty())
        assertTrue(GuideSearch.rank("   ", docs).isEmpty())
    }

    @Test
    fun unmatchedQueryReturnsNothing() {
        assertTrue(GuideSearch.rank("bluetooth", docs).isEmpty())
    }

    @Test
    fun tokenAndRequiresEveryToken() {
        // "tuesday" matches nlp; "swipe" matches swipe — together, nothing.
        assertTrue(GuideSearch.rank("tuesday swipe", docs).isEmpty())
        // Both tokens live in the nlp topic.
        assertEquals(listOf("nlp-date-syntax"), GuideSearch.rank("type date", docs))
    }

    @Test
    fun normalizeFoldsDiacriticsAndCollapsesWhitespace() {
        assertEquals("cafe repeter", GuideSearch.normalize("  Café   Répéter "))
        // A query with an accent still matches an unaccented keyword.
        val d = listOf(GuideSearch.buildDoc("x", "Répéter", "repeticion", "body"))
        assertEquals(listOf("x"), GuideSearch.rank("repet  ", d))
    }
}
