package app.pogopaste.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import kotlin.math.max
import kotlin.math.min

/**
 * A grid of symbol keys drawn on one canvas. One View instead of ~70 keeps inflation, measure and
 * draw cheap, which matters because the keyboard is rebuilt every time it opens with new settings.
 *
 * Exposes each key to TalkBack through a virtual-view [AccessibilityNodeProvider].
 */
@SuppressLint("ViewConstructor")
class KeyGridView(
    context: Context,
    private val palette: Palette,
    private val listener: Listener,
) : View(context) {

    interface Listener {
        fun onKeyDown(sym: Sym)
        fun onKeyTap(sym: Sym)
        fun onKeyLongPress(sym: Sym)
        fun onKeyRelease()

        /** After a long-press, the finger keeps moving (screen coordinates) — slide-to-pick. */
        fun onLongPressDrag(rawX: Float, rawY: Float) {}

        /** After a long-press, the finger lifted. */
        fun onLongPressRelease(rawX: Float, rawY: Float) {}
    }

    /** [maxRows] > 0 truncates the section to that many rows at whatever column count fits. */
    class Section(val title: String?, val items: List<Sym>, val dense: Boolean = false, val maxRows: Int = 0)

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    var sections: List<Section> = emptyList()
        set(v) {
            field = v
            a11yFocused = NO_ID
            hovered = NO_ID
            requestLayout()
            invalidate()
        }

    /** Print each key's description under the glyph. */
    var captions = false
        set(v) { field = v; requestLayout() }

    /** Stretch rows to fill this many pixels (paged layout). 0 = natural height. */
    var fillHeight = 0
        set(v) { field = v; requestLayout() }

    private class Cell(val sym: Sym, val rect: RectF, var caption: CharSequence = "")
    private class Header(val text: String, val y: Float)

    private val cells = ArrayList<Cell>()
    private val headers = ArrayList<Header>()

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(10.5f)
    }
    private val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(11f)
        typeface = Typeface.DEFAULT_BOLD
        color = palette.subtext
        letterSpacing = 0.06f
    }
    private val textBounds = Rect()

    private val gap = dp(4f)
    private val radius = dp(6f)
    private val headerH = dp(20f)

    private var pressed: Cell? = null
    private var longPressed = false
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressRunnable = Runnable {
        pressed?.let {
            longPressed = true
            // Keep the scroller from stealing the slide up into the alternates strip.
            parent?.requestDisallowInterceptTouchEvent(true)
            listener.onKeyLongPress(it.sym)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, layoutCells(w))
    }

    /** Lays every key out for [width] and returns the total height needed. */
    private fun layoutCells(width: Int): Int {
        cells.clear()
        headers.clear()
        if (width <= 0) return 0
        val usable = width - paddingLeft - paddingRight
        val showHeaders = sections.size > 1 || sections.firstOrNull()?.title != null
        var y = paddingTop.toFloat()
        for (s in sections) {
            val capt = captions && !s.dense
            val minW = dp(if (capt) 64f else 40f)
            val cols = max(1, (usable / minW).toInt())
            val keyW = usable / cols.toFloat()
            val items = if (s.maxRows > 0) s.items.take(cols * s.maxRows) else s.items
            val rows = (items.size + cols - 1) / cols
            var keyH = dp(if (capt) 56f else 46f)
            if (fillHeight > 0 && rows > 0) {
                keyH = ((fillHeight - paddingTop - paddingBottom) / rows.toFloat())
                    .coerceIn(dp(40f), dp(if (capt) 76f else 64f))
            }
            if (showHeaders && s.title != null) {
                headers += Header(s.title.uppercase(), y + headerH - dp(6f))
                y += headerH
            }
            items.forEachIndexed { i, sym ->
                val c = i % cols
                val r = i / cols
                val left = paddingLeft + c * keyW
                val top = y + r * keyH
                val cell = Cell(sym, RectF(left + gap / 2, top + gap / 2, left + keyW - gap / 2, top + keyH - gap / 2))
                if (capt && sym.name.isNotEmpty()) {
                    cell.caption = TextUtils.ellipsize(sym.name, captionPaint, cell.rect.width() - dp(4f), TextUtils.TruncateAt.END)
                }
                cells += cell
            }
            y += rows * keyH
        }
        return (y + paddingBottom).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        for (h in headers) canvas.drawText(h.text, paddingLeft + gap, h.y, headerPaint)
        for (cell in cells) {
            val sym = cell.sym
            val isPressed = cell === pressed
            val bg = sym.color ?: palette.key
            keyPaint.color = if (isPressed) blend(bg, palette.keyPressed) else bg
            canvas.drawRoundRect(cell.rect, radius, radius, keyPaint)

            val fg = if (sym.color != null) Color.WHITE else palette.text
            val hasCaption = cell.caption.isNotEmpty()
            val glyphSize = min(cell.rect.height() * if (hasCaption) 0.42f else 0.5f, dp(26f))
            glyphPaint.textSize = glyphSize
            glyphPaint.color = fg
            glyphPaint.getTextBounds(sym.symbol, 0, sym.symbol.length, textBounds)
            val glyphCenterY = if (hasCaption) cell.rect.top + cell.rect.height() * 0.40f else cell.rect.centerY()
            canvas.drawText(sym.symbol, cell.rect.centerX(), glyphCenterY - textBounds.exactCenterY(), glyphPaint)
            if (hasCaption) {
                captionPaint.color = if (sym.color != null) 0xE6FFFFFF.toInt() else palette.subtext
                canvas.drawText(cell.caption, 0, cell.caption.length, cell.rect.centerX(), cell.rect.bottom - dp(7f), captionPaint)
            }
        }
    }

    private fun blend(a: Int, b: Int): Int {
        fun mix(x: Int, y: Int) = (x + y) / 2
        return Color.rgb(mix(Color.red(a), Color.red(b)), mix(Color.green(a), Color.green(b)), mix(Color.blue(a), Color.blue(b)))
    }

    private fun cellAt(x: Float, y: Float): Cell? {
        // Keys have small gaps; accept touches in the gap so fast taps never fall through.
        for (c in cells) {
            if (x >= c.rect.left - gap / 2 && x <= c.rect.right + gap / 2 &&
                y >= c.rect.top - gap / 2 && y <= c.rect.bottom + gap / 2) return c
        }
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val c = cellAt(e.x, e.y) ?: return false
                downX = e.x; downY = e.y
                pressed = c
                longPressed = false
                listener.onKeyDown(c.sym)
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val p = pressed ?: return true
                if (longPressed) listener.onLongPressDrag(e.rawX, e.rawY)
                else if ((Math.abs(e.x - downX) > touchSlop || Math.abs(e.y - downY) > touchSlop)) {
                    if (cellAt(e.x, e.y) !== p) clearPress(notify = true)
                }
            }
            MotionEvent.ACTION_UP -> {
                val p = pressed
                val wasLong = longPressed
                clearPress(notify = !wasLong)
                if (p != null && !wasLong) {
                    playSoundEffect(android.view.SoundEffectConstants.CLICK)
                    listener.onKeyTap(p.sym)
                } else if (p != null) {
                    listener.onLongPressRelease(e.rawX, e.rawY)
                }
            }
            MotionEvent.ACTION_CANCEL -> clearPress(notify = !longPressed)
        }
        return true
    }

    private fun clearPress(notify: Boolean) {
        removeCallbacks(longPressRunnable)
        if (pressed != null) {
            pressed = null
            invalidate()
            if (notify) listener.onKeyRelease()
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    // --- Accessibility: one virtual node per key ---------------------------------------------
    // Hand-rolled equivalent of AndroidX's ExploreByTouchHelper (we don't ship AndroidX):
    // virtual nodes, accessibility focus, and hover hit-testing for touch exploration.

    private var a11yFocused = NO_ID
    private var hovered = NO_ID
    private val a11yManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    private fun describe(cell: Cell) =
        if (cell.sym.name.isNotEmpty()) "${cell.sym.name}, ${cell.sym.symbol}" else cell.sym.symbol

    private fun indexAt(x: Float, y: Float): Int {
        val c = cellAt(x, y) ?: return NO_ID
        return cells.indexOf(c)
    }

    @Suppress("DEPRECATION")
    private fun sendVirtualEvent(id: Int, type: Int) {
        if (!a11yManager.isEnabled) return
        val p = parent ?: return
        val cell = cells.getOrNull(id) ?: return
        val ev = AccessibilityEvent.obtain(type)
        ev.packageName = context.packageName
        ev.className = BUTTON
        ev.contentDescription = describe(cell)
        ev.setSource(this, id)
        p.requestSendAccessibilityEvent(this, ev)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if (!a11yManager.isEnabled || !a11yManager.isTouchExplorationEnabled) return super.dispatchHoverEvent(event)
        when (event.action) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                val id = indexAt(event.x, event.y)
                setHovered(id)
                return id != NO_ID || super.dispatchHoverEvent(event)
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                if (hovered != NO_ID) { setHovered(NO_ID); return true }
            }
        }
        return super.dispatchHoverEvent(event)
    }

    private fun setHovered(id: Int) {
        if (id == hovered) return
        val old = hovered
        hovered = id
        if (id != NO_ID) sendVirtualEvent(id, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
        if (old != NO_ID) sendVirtualEvent(old, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
    }

    private val a11y = object : AccessibilityNodeProvider() {
        @Suppress("DEPRECATION")
        override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
            if (virtualViewId == View.NO_ID) {
                val info = AccessibilityNodeInfo.obtain(this@KeyGridView)
                onInitializeAccessibilityNodeInfo(info)
                for (i in cells.indices) info.addChild(this@KeyGridView, i)
                return info
            }
            val cell = cells.getOrNull(virtualViewId) ?: return null
            val info = AccessibilityNodeInfo.obtain(this@KeyGridView, virtualViewId)
            info.setParent(this@KeyGridView)
            info.packageName = context.packageName
            info.className = BUTTON
            info.contentDescription = describe(cell)
            val r = Rect()
            cell.rect.roundOut(r)
            info.setBoundsInParent(r)
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            r.offset(loc[0], loc[1])
            info.setBoundsInScreen(r)
            info.isEnabled = true
            info.isVisibleToUser = true
            info.isClickable = true
            info.isFocusable = true
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            if (cell.sym.alternates.isNotEmpty()) {
                info.isLongClickable = true
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK)
            }
            if (a11yFocused == virtualViewId) {
                info.isAccessibilityFocused = true
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
            } else {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS)
            }
            return info
        }

        override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            if (virtualViewId == View.NO_ID) return performAccessibilityAction(action, arguments)
            val cell = cells.getOrNull(virtualViewId) ?: return false
            return when (action) {
                AccessibilityNodeInfo.ACTION_CLICK -> {
                    listener.onKeyTap(cell.sym)
                    sendVirtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
                    true
                }
                AccessibilityNodeInfo.ACTION_LONG_CLICK -> {
                    listener.onKeyLongPress(cell.sym)
                    sendVirtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)
                    true
                }
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                    if (a11yFocused == virtualViewId) return false
                    val old = a11yFocused
                    a11yFocused = virtualViewId
                    if (old != NO_ID) sendVirtualEvent(old, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                    sendVirtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                    invalidate()
                    true
                }
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                    if (a11yFocused != virtualViewId) return false
                    a11yFocused = NO_ID
                    sendVirtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                    invalidate()
                    true
                }
                else -> false
            }
        }
    }

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = a11y

    private companion object {
        const val BUTTON = "android.widget.Button"
    }
}
