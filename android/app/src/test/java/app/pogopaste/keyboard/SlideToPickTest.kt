package app.pogopaste.keyboard

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Hold a symbol, slide onto an alternate in the strip, lift. Driven through a real window so
 * long-press timers and screen coordinates behave as on a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28, 35])
class SlideToPickTest {

    private class FakeHost : PogoKeyboardView.Host {
        val log = ArrayList<String>()
        override fun commitSymbol(sym: Sym) { log += "sym:${sym.symbol}" }
        override fun commitSearch(entry: SearchEntry) {}
        override fun commitRaw(text: String) {}
        override fun deleteBackward() {}
        override fun enter() {}
        override fun switchBack() {}
        override fun showPicker() {}
        override fun openSettings() {}
        override fun haptic(v: View) {}
    }

    private val host = FakeHost()
    private lateinit var root: View
    private lateinit var grid: KeyGridView
    private lateinit var strip: HorizontalScrollView
    private var downTime = 0L
    private val density get() = root.resources.displayMetrics.density

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val prefs = Prefs(activity).apply { sp.edit().clear().commit(); layout = Layout.COMPACT }
        val catalog = Catalog.parse(File("../../src/_data/symbols.json").readText(), File("../../src/_data/search.json").readText())
        val keyboard = PogoKeyboardView(activity, host, catalog, prefs)
        activity.setContentView(keyboard)
        ShadowLooper.idleMainLooper()
        root = activity.window.decorView
        grid = keyboard.all().filterIsInstance<KeyGridView>().single()
        // In the compact Symbols tab the only horizontal scroller is the alternates strip.
        strip = keyboard.all().filterIsInstance<HorizontalScrollView>().single()
    }

    private fun View.all(): List<View> {
        val out = ArrayList<View>()
        val stack = ArrayDeque<View>().apply { add(this@all) }
        while (stack.isNotEmpty()) {
            val v = stack.removeLast()
            out += v
            if (v is ViewGroup) for (i in 0 until v.childCount) stack.add(v.getChildAt(i))
        }
        return out
    }

    private fun loc(v: View) = IntArray(2).also { v.getLocationInWindow(it) }

    /** Center of the first key (Bug: ഋ, alternates ⁂ ප), in window coordinates. */
    private fun firstKey(): Pair<Float, Float> {
        val g = loc(grid)
        return (g[0] + 20 * density) to (g[1] + 42 * density)
    }

    private fun altKey(i: Int): View = (strip.getChildAt(0) as LinearLayout).getChildAt(i)

    private fun center(v: View): Pair<Float, Float> {
        val l = loc(v)
        return (l[0] + v.width / 2f) to (l[1] + v.height / 2f)
    }

    private fun send(action: Int, vararg pts: Pair<Float, Float>, actionIndex: Int = 0, ids: IntArray? = null) {
        val props = Array(pts.size) { MotionEvent.PointerProperties().apply { id = ids?.get(it) ?: it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(pts.size) { MotionEvent.PointerCoords().apply { x = pts[it].first; y = pts[it].second; pressure = 1f; size = 1f } }
        val a = action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), a, pts.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        root.dispatchTouchEvent(e)
        e.recycle()
        ShadowLooper.idleMainLooper()
    }

    private fun holdFirstKey(): Pair<Float, Float> {
        val start = firstKey()
        downTime = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, start)
        ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout() + 50L, TimeUnit.MILLISECONDS)
        assertEquals("strip open", View.VISIBLE, (strip.parent as View).visibility)
        return start
    }

    @Test
    fun `sliding onto an alternate and lifting inserts it`() {
        holdFirstKey()
        val target = center(altKey(1))
        send(MotionEvent.ACTION_MOVE, target)
        assertTrue(altKey(1).isPressed)
        send(MotionEvent.ACTION_UP, target)
        assertEquals(listOf("sym:⁂"), host.log)
    }

    @Test
    fun `lifting far above the strip inserts nothing`() {
        holdFirstKey()
        val (x, _) = center(altKey(1))
        val above = x to (loc(strip)[1] - 80 * density)
        send(MotionEvent.ACTION_MOVE, above)
        send(MotionEvent.ACTION_UP, above)
        assertEquals(emptyList<String>(), host.log)
    }

    @Test
    fun `a second finger cannot finish the slide`() {
        val start = holdFirstKey()
        val other = start.first + 100 * density to start.second
        send(MotionEvent.ACTION_POINTER_DOWN, start, other, actionIndex = 1)
        send(MotionEvent.ACTION_POINTER_UP, start, other, actionIndex = 0) // finger A lifts on the grid
        val target = center(altKey(1))
        send(MotionEvent.ACTION_MOVE, target, ids = intArrayOf(1)) // only finger B (id 1) remains
        send(MotionEvent.ACTION_UP, target, ids = intArrayOf(1))
        assertEquals(emptyList<String>(), host.log)
    }

    @Test
    fun `cancel clears the highlight and inserts nothing`() {
        holdFirstKey()
        val target = center(altKey(1))
        send(MotionEvent.ACTION_MOVE, target)
        assertTrue(altKey(1).isPressed)
        send(MotionEvent.ACTION_CANCEL, target)
        assertFalse(altKey(1).isPressed)
        assertEquals(emptyList<String>(), host.log)
    }
}
