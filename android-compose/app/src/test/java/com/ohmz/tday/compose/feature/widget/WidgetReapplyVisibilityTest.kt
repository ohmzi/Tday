package com.ohmz.tday.compose.feature.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The regression test for "resizing the widget duplicates the watermark in the background".
 *
 * A host re-applies each new RemoteViews onto the views it already shows instead of inflating
 * the layout again, so the XML's `android:visibility="gone"` is only the state of the FIRST
 * render. A render that shows a view only when it is needed leaves it showing forever after:
 * resizing MEDIUM to TALL kept the 148dp watermark and added the 208dp one on top.
 *
 * Not observable in a JVM test — it needs a host's view tree — so what is pinned is the contract
 * that prevents it: every gone-by-default view in the rendered layouts has its visibility set on
 * every render, through `setVisible(id, Boolean)`, which is the only visibility call in the file.
 */
class WidgetReapplyVisibilityTest {

    @Test
    fun `should route every visibility change through setVisible`() {
        val calls = Regex("""setViewVisibility\(""").findAll(rendererSource).count()
        assertEquals(
            "TaskWidgetDesign.kt should call setViewVisibility only inside setVisible, so every " +
                "view is explicitly shown or hidden on each render",
            1,
            calls,
        )
    }

    @Test
    fun `should set every gone-by-default view on every render`() {
        val toggled = Regex("""setVisible\(\s*R\.id\.(\w+)""").findAll(rendererSource)
            .map { it.groupValues[1] }
            .toSet()
        // The watermarks are switched as a set, by bucket, through taskWidgetWatermarkViewId.
        val watermarks = Regex("""R\.id\.(widget_watermark_\w+)""").findAll(rendererSource)
            .map { it.groupValues[1] }
            .toSet()

        val goneByDefault = RENDERED_LAYOUTS.flatMap(::goneByDefaultIds).toSet()
        assertTrue("no gone-by-default views found in $RENDERED_LAYOUTS", goneByDefault.isNotEmpty())
        assertEquals(
            "these views start gone in XML but no render sets their visibility, so a host that " +
                "re-applies a render keeps whatever an earlier one left showing",
            emptySet<String>(),
            goneByDefault - toggled - watermarks,
        )
    }

    private fun goneByDefaultIds(layout: String): List<String> {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(resDir, "layout/$layout.xml"))
        val elements = document.getElementsByTagName("*")
        return (0 until elements.length)
            .map { elements.item(it) as Element }
            .filter { it.getAttributeNS(ANDROID_NS, "visibility") == "gone" }
            .map { it.getAttributeNS(ANDROID_NS, "id").substringAfter("/") }
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        val RENDERED_LAYOUTS = listOf("widget_task", "widget_task_list_row")

        val mainDir: File = generateSequence(File(".").canonicalFile) { it.parentFile }
            .flatMap { sequenceOf(File(it, "src/main"), File(it, "app/src/main")) }
            .firstOrNull { File(it, "res").isDirectory }
            ?: error("could not locate app/src/main from ${File(".").canonicalPath}")

        val resDir: File = File(mainDir, "res")

        val rendererSource: String =
            File(mainDir, "java/com/ohmz/tday/compose/feature/widget/TaskWidgetDesign.kt").readText()
    }
}
