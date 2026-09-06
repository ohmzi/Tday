package com.ohmz.tday.compose.feature.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The regression test for "the widget-picker preview card has a thin light outline around it".
 *
 * Since Android 12 the launcher clips a widget's picker preview to its own enforced corner radius
 * — `android:dimen/system_app_widget_background_radius`, capped by Launcher3's
 * `enforced_rounded_corner_max_radius`, both 16dp by default. The preview art used a hardcoded
 * 24dp, which is ROUNDER than that clip, so our fill landed wholly inside the clip and the
 * crescent between the two arcs was a hole in our own artwork. The picker paints an opaque light
 * surface behind the card, so each corner leaked a light crescent and the card read as outlined.
 *
 * Two surfaces, two rules, and the difference between them is the point:
 *
 *  - the **picker preview** art may never be rounder than the host's clip, or the hole comes back;
 *  - the **placed widget**'s own surface is a design value. It is NOT clamped here. Where the host
 *    clips, a radius rounder than the clip is what the silhouette actually becomes (our fill is
 *    inside the clip, so our arc is the visible edge); on API 26-30, where nothing clips, it is
 *    the silhouette outright. Either way, lowering it changes every widget already on a home
 *    screen, which is not what a picker fix is allowed to do.
 *
 * So this test asserts that the two surfaces stay *separate* as well as tokenised. None of it is
 * observable in a JVM test — the defect only exists once a host composites the layout — so what is
 * pinned is the resource contract that prevents it.
 *
 * The surfaces are **discovered**, not listed: the manifest's `android.appwidget.provider`
 * meta-data gives every descriptor, each descriptor names its `initialLayout` (placed) and
 * `previewLayout` (picker), and each layout's root `android:background` names the drawable. A new
 * widget, size, or preview layout is therefore covered the moment it is wired up, without editing
 * this test. A drawable that no widget roots on is not a widget surface and is not checked.
 */
class WidgetCornerRadiusTest {

    @Test
    fun `should keep the picker preview radius at or under the platform enforced radius`() {
        val radiusDp = declaredRadiusDp(PICKER_PREVIEW_TOKEN)
        assertTrue(
            "$PICKER_PREVIEW_TOKEN is ${radiusDp}dp; anything above ${PLATFORM_ENFORCED_RADIUS_DP}dp " +
                "can be rounder than the host's clip and re-opens the corner leak",
            radiusDp <= PLATFORM_ENFORCED_RADIUS_DP,
        )
    }

    @Test
    fun `should draw every picker preview surface through the picker token`() {
        val surfaces = surfacesFor(AppWidgetDescriptor::previewLayout)
        assertTrue("no previewLayout backgrounds discovered — is the discovery broken?", surfaces.isNotEmpty())
        surfaces.forEach { assertSingleRadius(it, PICKER_PREVIEW_TOKEN) }
    }

    @Test
    fun `should draw every placed widget surface through the placed token`() {
        val surfaces = surfacesFor(AppWidgetDescriptor::initialLayout)
        assertTrue("no initialLayout backgrounds discovered — is the discovery broken?", surfaces.isNotEmpty())
        surfaces.forEach { assertSingleRadius(it, PLACED_SURFACE_TOKEN) }
    }

    /**
     * The reported bug was in the picker only, and the user's placed widget was correct. Sharing
     * one drawable between the two is how a picker-only fix silently re-rounds every home screen.
     */
    @Test
    fun `should not share a drawable between the picker preview and the placed widget`() {
        val shared = surfacesFor(AppWidgetDescriptor::previewLayout)
            .intersect(surfacesFor(AppWidgetDescriptor::initialLayout))
        assertEquals(
            "these drawables back both a previewLayout and an initialLayout, so the picker's " +
                "radius rule would also govern the placed widget's silhouette",
            emptySet<String>(),
            shared,
        )
    }

    /** The Glance runtime paints the same surface the initialLayout does, or the widget reflows. */
    @Test
    fun `should paint the Glance runtime background with the placed widget surface`() {
        val placed = surfacesFor(AppWidgetDescriptor::initialLayout)
        val design = File(widgetSourceDir, "TaskWidgetDesign.kt").readText()
        val painted = Regex("""\.background\(ImageProvider\(R\.drawable\.(\w+)\)\)""")
            .findAll(design)
            .map { it.groupValues[1] }
            .toSet()
        assertTrue(
            "TaskWidgetDesign paints $painted; the initialLayouts root on $placed. The composed " +
                "widget and the layout it replaces must use the same surface drawable",
            painted.any { it in placed },
        )
    }

    /**
     * `setWidgetPreview` (API 35+) is the second way our preview art reaches the picker. Keeping
     * its layouts inside the descriptors' `previewLayout` set is what makes the discovery above
     * cover that route too.
     */
    @Test
    fun `should publish only descriptor preview layouts through setWidgetPreview`() {
        val published = Regex("""R\.layout\.(\w+)""")
            .findAll(File(widgetSourceDir, "TodayTasksWidgetPreviewPublisher.kt").readText())
            .map { it.groupValues[1] }
            .toSet()
        val declared = appWidgetDescriptors().mapNotNull { it.previewLayout }.toSet()

        assertTrue("TodayTasksWidgetPreviewPublisher names no layouts", published.isNotEmpty())
        assertEquals(
            "these layouts are pushed to the picker by setWidgetPreview but are no descriptor's " +
                "previewLayout, so their surface radius is unchecked",
            emptySet<String>(),
            published - declared,
        )
    }

    /** Every descriptor must go through the discovery above, or it dodges both rules. */
    @Test
    fun `should declare both an initialLayout and a previewLayout on every descriptor`() {
        val descriptors = appWidgetDescriptors()
        assertTrue("no appwidget-provider meta-data found in the manifest", descriptors.isNotEmpty())
        descriptors.forEach {
            assertTrue("${it.name} declares no android:initialLayout", it.initialLayout != null)
            assertTrue("${it.name} declares no android:previewLayout", it.previewLayout != null)
        }
    }

    private fun surfacesFor(layoutOf: (AppWidgetDescriptor) -> String?): Set<String> =
        appWidgetDescriptors()
            .mapNotNull(layoutOf)
            .toSet()
            .map { rootBackgroundDrawable(it) }
            .toSet()

    private fun assertSingleRadius(drawable: String, token: String) {
        val markup = File(resDir, "drawable/$drawable.xml").readText()
        val radii = Regex("""<corners android:radius="([^"]+)"""").findAll(markup)
            .map { it.groupValues[1] }
            .toList()

        assertEquals("$drawable should declare exactly one corner radius", 1, radii.size)
        assertEquals(
            "$drawable must take its radius from @dimen/$token, not a literal or another token",
            "@dimen/$token",
            radii.single(),
        )
    }

    private fun declaredRadiusDp(token: String): Int {
        val declared = Regex("""<dimen name="$token">(\d+)dp</dimen>""")
            .find(File(resDir, "values/dimens.xml").readText())
        assertTrue("$token must be declared in values/dimens.xml", declared != null)
        return declared!!.groupValues[1].toInt()
    }

    /** The `@drawable/…` on the root element of a layout, which is the widget's outer surface. */
    private fun rootBackgroundDrawable(layout: String): String {
        val root = parse(File(resDir, "layout/$layout.xml"))
        val background = root.getAttribute("android:background")
        assertTrue(
            "the root of layout/$layout.xml declares android:background=\"$background\"; a widget " +
                "root must paint its surface with a @drawable so its corner radius is checkable",
            background.startsWith("@drawable/"),
        )
        return background.removePrefix("@drawable/")
    }

    private fun appWidgetDescriptors(): List<AppWidgetDescriptor> {
        val manifest = parse(File(mainDir, "AndroidManifest.xml"))
        val metaData = manifest.getElementsByTagName("meta-data")
        return (0 until metaData.length)
            .map { metaData.item(it) as Element }
            .filter { it.getAttribute("android:name") == "android.appwidget.provider" }
            .map { it.getAttribute("android:resource").removePrefix("@xml/") }
            .distinct()
            .map { name ->
                val provider = parse(File(resDir, "xml/$name.xml"))
                AppWidgetDescriptor(
                    name = name,
                    initialLayout = provider.layoutAttribute("android:initialLayout"),
                    previewLayout = provider.layoutAttribute("android:previewLayout"),
                )
            }
    }

    private data class AppWidgetDescriptor(
        val name: String,
        val initialLayout: String?,
        val previewLayout: String?,
    )

    private companion object {
        /**
         * The AOSP default of both `system_app_widget_background_radius` and Launcher3's
         * `enforced_rounded_corner_max_radius`, so it is the smallest radius a host is expected
         * to clip a picker preview to.
         */
        const val PLATFORM_ENFORCED_RADIUS_DP = 16

        const val PICKER_PREVIEW_TOKEN = "tday_widget_picker_preview_corner_radius"
        const val PLACED_SURFACE_TOKEN = "tday_widget_surface_corner_radius"

        fun parse(file: File): Element =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement

        fun Element.layoutAttribute(name: String): String? =
            getAttribute(name).removePrefix("@layout/").ifEmpty { null }

        /** The unit test's working directory is the Gradle module dir, but do not rely on it. */
        val mainDir: File = generateSequence(File(".").canonicalFile) { it.parentFile }
            .flatMap { sequenceOf(File(it, "src/main"), File(it, "app/src/main")) }
            .firstOrNull { File(it, "res").isDirectory }
            ?: error("could not locate app/src/main from ${File(".").canonicalPath}")

        val resDir: File = File(mainDir, "res")

        val widgetSourceDir: File =
            File(mainDir, "java/com/ohmz/tday/compose/feature/widget")
    }
}
