package app.pogopaste.keyboard

import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ScrollView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Real-View checks for the keyboard surface, across the min, a mid, and the newest supported API. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28, 35])
class KeyboardViewTest {

    private class FakeHost : PogoKeyboardView.Host {
        val log = ArrayList<String>()
        override fun commitSymbol(sym: Sym) { log += "sym:${sym.symbol}" }
        override fun commitSearch(entry: SearchEntry) { log += "search:${entry.name}" }
        override fun commitRaw(text: String) { log += "raw:$text" }
        override fun deleteBackward() { log += "delete" }
        override fun enter() { log += "enter" }
        override fun switchBack() { log += "switch" }
        override fun showPicker() { log += "picker" }
        override fun openSettings() { log += "settings" }
        override fun haptic(v: View) {}
    }

    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var prefs: Prefs
    private lateinit var catalog: Catalog
    private val host = FakeHost()

    @Before
    fun setUp() {
        prefs = Prefs(context)
        prefs.sp.edit().clear().commit()
        catalog = Catalog.parse(File("../../src/_data/symbols.json").readText(), File("../../src/_data/search.json").readText())
    }

    private fun build(widthDp: Int = 320): PogoKeyboardView {
        val v = PogoKeyboardView(context, host, catalog, prefs)
        val d = context.resources.displayMetrics.density
        v.measure(
            View.MeasureSpec.makeMeasureSpec((widthDp * d).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        return v
    }

    private fun View.descendants(): List<View> {
        val out = ArrayList<View>()
        val stack = ArrayDeque<View>().apply { add(this@descendants) }
        while (stack.isNotEmpty()) {
            val v = stack.removeLast()
            out += v
            if (v is ViewGroup) for (i in 0 until v.childCount) stack.add(v.getChildAt(i))
        }
        return out
    }

    private inline fun <reified T : View> View.find(): List<T> = descendants().filterIsInstance<T>()

    private fun View.byDescription(desc: String): View? {
        if (contentDescription?.toString() == desc) return this
        if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).byDescription(desc)?.let { return it }
        return null
    }

    @Test
    fun `every layout and tab builds without crashing`() {
        for (layout in Layout.entries) for (tab in 0..1) {
            prefs.layout = layout
            prefs.lastTab = tab
            prefs.recents = listOf("♨", "✨")
            build().onStartInput(0)
        }
    }

    @Test
    fun `paged symbols stay reachable on the shortest keyboard`() {
        prefs.layout = Layout.PAGED
        prefs.heightDp = Prefs.MIN_HEIGHT_DP
        prefs.lastTab = 0
        val grid = build().find<KeyGridView>().single()
        val scroller = grid.parent as ScrollView
        assertTrue("grid taller than its page", grid.measuredHeight > scroller.height)
        assertTrue(scroller.canScrollVertically(1))
    }

    @Test
    fun `delete is activatable by accessibility click`() {
        val del = build().byDescription("Delete")
        assertNotNull(del)
        assertTrue(del!!.performClick())
        assertEquals(listOf("delete"), host.log)
    }

    @Test
    fun `symbol keys take accessibility focus and click`() {
        prefs.layout = Layout.COMPACT
        val grid = build().find<KeyGridView>().first()
        val provider = grid.accessibilityNodeProvider
        assertTrue(provider.performAction(0, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null))
        assertTrue(provider.createAccessibilityNodeInfo(0)!!.isAccessibilityFocused)
        assertFalse(provider.performAction(0, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null))
        assertTrue(provider.performAction(0, AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null))
        assertFalse(provider.createAccessibilityNodeInfo(0)!!.isAccessibilityFocused)
        assertTrue(provider.performAction(0, AccessibilityNodeInfo.ACTION_CLICK, null))
        assertEquals("sym:${catalog.symbols[0].items[0].symbol}", host.log.single())
    }

    @Test
    fun `bottom row operators commit raw text`() {
        build().byDescription("and")!!.performClick()
        assertEquals(listOf("raw:&"), host.log)
    }
}
