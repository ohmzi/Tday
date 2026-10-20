package com.ohmz.tday.shared.guide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuideCatalogTest {

    @Test
    fun topicIdsAreUnique() {
        val ids = GuideCatalog.topics.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate topic id in GuideCatalog")
    }

    @Test
    fun everyTopicAppliesToAtLeastOnePlatform() {
        GuideCatalog.topics.forEach {
            assertTrue(it.platforms.isNotEmpty(), "topic ${it.id} has no platforms")
        }
    }

    @Test
    fun everyTopicHasABody() {
        GuideCatalog.topics.forEach {
            assertTrue(it.body.isNotEmpty(), "topic ${it.id} has an empty body")
        }
    }

    @Test
    fun stepBlocksHaveKeysAndSingleBlocksHaveExactlyOne() {
        GuideCatalog.topics.forEach { topic ->
            topic.body.forEach { block ->
                assertTrue(block.keys.isNotEmpty(), "empty block in ${topic.id}")
                if (block.type != GuideBlockType.STEPS) {
                    assertEquals(1, block.keys.size, "non-steps block in ${topic.id} must hold one key")
                }
            }
        }
    }

    @Test
    fun stringKeysAreWellFormedAndUnique() {
        GuideCatalog.topics.forEach { topic ->
            topic.allStringKeys().forEach { key ->
                assertTrue(key.startsWith("guide.topics.${topic.id}."), "stray key $key in ${topic.id}")
            }
        }
        val allKeys = GuideCatalog.topics.flatMap { it.allStringKeys() }
        assertEquals(allKeys.size, allKeys.toSet().size, "duplicate i18n key across catalog")
    }

    @Test
    fun platformFilteringIsolatesPlatformOnlyTopics() {
        val webIds = GuideCatalog.topicsFor(GuidePlatform.WEB).map { it.id }
        val androidIds = GuideCatalog.topicsFor(GuidePlatform.ANDROID).map { it.id }
        assertTrue(GuideTopicIds.KEYBOARD_SUBMIT in webIds)
        assertFalse(GuideTopicIds.KEYBOARD_SUBMIT in androidIds, "web-only shortcut leaked to Android")
        assertFalse(GuideTopicIds.CARPLAY in webIds, "iOS-only CarPlay leaked to web")
        assertTrue(GuideTopicIds.ANDROID_CAR in androidIds)
    }

    @Test
    fun whatsNewSurfacesTheShippedVersion() {
        val fresh = GuideCatalog.whatsNew("0.3.0", GuidePlatform.WEB).map { it.id }
        assertTrue(GuideTopicIds.PUSH_NOTIFICATIONS in fresh, "0.3.0 push feature missing from What's New")
        assertTrue(GuideCatalog.whatsNew("0.1.0", GuidePlatform.WEB).isEmpty())
    }

    @Test
    fun deepLinkResolvesPerPlatform() {
        val overdue = GuideCatalog.byId.getValue(GuideTopicIds.OVERDUE_VIEW).deepLink!!
        assertEquals("overdue", overdue.forPlatform(GuidePlatform.WEB))
        assertEquals("todos/overdue", overdue.forPlatform(GuidePlatform.ANDROID))
        assertEquals("overdueTodos", overdue.forPlatform(GuidePlatform.IOS))
    }
}
