package com.ohmz.tday.compose.feature.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The regression test for "the widget-picker preview card has a thin light outline around it".
 *
 * Since Android 12 the host clips both a placed widget and its picker preview to the launcher's
 * own enforced corner radius — `android:dimen/system_app_widget_background_radius`, capped by
 * Launcher3's `enforced_rounded_corner_max_radius`, both 16dp by default. Our widget surfaces
 * used a hardcoded 24dp, which is ROUNDER than that clip. A rounder-than-the-clip background does
 * not render rounder: the host's clip keeps the wider corner, and the gap between the two arcs is
 * a hole in our own artwork through which whatever the host paints behind the widget shows. On the
 * reporting device the picker paints an opaque light surface there, so each corner leaked a
 * light crescent and the card read as outlined.
 *
 * This is unobservable in a JVM test — the defect only exists once a host composites the layout —
 * so what is pinned here is the resource contract that prevents it: every widget surface takes its
 * radius from one token, and that token stays at or below the platform's own default. Nothing here
 * needs a device, and nothing here can drift silently back to a literal.
 */
class WidgetCornerRadiusTest {

    @Test
    fun `should keep the widget corner radius at or under the platform enforced radius`() {
        val declared = Regex("""<dimen name="tday_widget_corner_radius">(\d+)dp</dimen>""")
            .find(File(resDir, "values/dimens.xml").readText())
        assertTrue("tday_widget_corner_radius must be declared in values/dimens.xml", declared != null)

        val radiusDp = declared!!.groupValues[1].toInt()
        assertTrue(
            "tday_widget_corner_radius is ${radiusDp}dp; anything above " +
                "${PLATFORM_ENFORCED_RADIUS_DP}dp can be rounder than the host's clip and " +
                "re-opens the corner leak",
            radiusDp <= PLATFORM_ENFORCED_RADIUS_DP,
        )
    }

    @Test
    fun `should draw every widget surface through that one token`() {
        WIDGET_SURFACE_DRAWABLES.forEach { name ->
            val markup = File(resDir, "drawable/$name").readText()
            val radii = Regex("""<corners android:radius="([^"]+)"""").findAll(markup)
                .map { it.groupValues[1] }
                .toList()

            assertEquals("$name should declare exactly one corner radius", 1, radii.size)
            assertEquals(
                "$name must take its radius from @dimen/tday_widget_corner_radius, not a literal",
                "@dimen/tday_widget_corner_radius",
                radii.single(),
            )
        }
    }

    private companion object {
        /**
         * The AOSP default of both `system_app_widget_background_radius` and Launcher3's
         * `enforced_rounded_corner_max_radius`, so it is the smallest radius a host is expected
         * to clip to.
         */
        const val PLATFORM_ENFORCED_RADIUS_DP = 16

        /** Every drawable that paints a widget's outermost surface. */
        val WIDGET_SURFACE_DRAWABLES = listOf(
            // The Glance runtime background (TaskWidgetDesign) and the static initialLayouts.
            "widget_preview_background.xml",
            // The widget-picker preview art, per kind.
            "widget_preview_bg_today.xml",
            "widget_preview_bg_floater.xml",
        )

        /** The unit test's working directory is the Gradle module dir, but do not rely on it. */
        val resDir: File = generateSequence(File(".").canonicalFile) { it.parentFile }
            .flatMap { sequenceOf(File(it, "src/main/res"), File(it, "app/src/main/res")) }
            .firstOrNull { it.isDirectory }
            ?: error("could not locate app/src/main/res from ${File(".").canonicalPath}")
    }
}
