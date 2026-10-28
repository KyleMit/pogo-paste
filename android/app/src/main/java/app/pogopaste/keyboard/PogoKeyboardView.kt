package app.pogopaste.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The whole keyboard surface, built in code (no XML inflation):
 *
 * ```
 * ┌ top bar ── [Symbols][Search]  hint/name of pressed key ……… [⚙] ┐  ← long-press swaps in an alternates strip
 * │ content (fixed height; scrolls or pages depending on Layout)   │
 * └ bottom ── [🌐][&][,][!][-][ space ][⌫][⏎] ─────────────────────┘
 * ```
 *
 * The overall height never changes between tabs so the host app doesn't re-layout under the user.
 */
@SuppressLint("ViewConstructor")
class PogoKeyboardView(
    context: Context,
    private val host: Host,
    private val catalog: Catalog,
    private val prefs: Prefs,
) : LinearLayout(context) {

    interface Host {
        fun commitSymbol(sym: Sym)
        fun commitSearch(entry: SearchEntry)
        fun commitRaw(text: String)
        fun deleteBackward()
        fun enter()
        fun switchBack()
        fun showPicker()
        fun openSettings()
        fun haptic(v: View)
    }

    private val palette = Palette.of(context)
    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density + 0.5f).toInt()

    private val layoutMode = prefs.layout
    private val contentHeight = dp(prefs.heightDp)

    private val tabSymbols: TextView
    private val tabSearch: TextView
    private val hint: TextView
    private val normalBar: LinearLayout
    private val altBar: LinearLayout
    private val altName: TextView
    private val altKeys: LinearLayout
    private val content = FrameLayout(context)
    private var enterIcon: ImageView? = null

    private var tab = prefs.lastTab.coerceIn(0, 1)

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.background)
        // Insets: on Android 15+ the IME window is edge-to-edge, so pad for the nav bar ourselves.
        setOnApplyWindowInsetsListener { v, insets ->
            val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsetBottom
            }
            v.setPadding(0, 0, 0, bottom)
            insets
        }

        // --- top bar -------------------------------------------------------------------------
        val bar = FrameLayout(context)
        normalBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(2), 0)
        }
        tabSymbols = tabView("Symbols") { selectTab(0) }
        tabSearch = tabView("Search") { selectTab(1) }
        hint = TextView(context).apply {
            setTextColor(palette.subtext)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(dp(8), 0, dp(8), 0)
        }
        val cog = iconButton(R.drawable.ic_settings, "Pogo Keyboard settings", palette.subtext) { host.openSettings() }
        normalBar.addView(tabSymbols, LayoutParams(LayoutParams.WRAP_CONTENT, dp(30)))
        normalBar.addView(tabSearch, LayoutParams(LayoutParams.WRAP_CONTENT, dp(30)).apply { leftMargin = dp(4) })
        normalBar.addView(hint, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        normalBar.addView(cog, LayoutParams(dp(44), LayoutParams.MATCH_PARENT))

        altBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = GONE
            setBackgroundColor(palette.keySpecial)
        }
        altName = TextView(context).apply {
            setTextColor(palette.subtext)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(110)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(6), 0)
        }
        altKeys = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val altScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(altKeys)
        }
        altBar.addView(iconButton(R.drawable.ic_close, "Close alternates", palette.subtext) { hideAlternates() },
            LayoutParams(dp(40), LayoutParams.MATCH_PARENT))
        altBar.addView(altName, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        altBar.addView(altScroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        bar.addView(normalBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        bar.addView(altBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dp(42)))

        // --- content ---------------------------------------------------------------------------
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, contentHeight))

        // --- bottom row ------------------------------------------------------------------------
        addView(bottomRow(), LayoutParams(LayoutParams.MATCH_PARENT, dp(50)))
    }

    /** Called each time the keyboard is shown for a field. */
    fun onStartInput(imeAction: Int) {
        hideAlternates()
        hint.text = ""
        enterIcon?.setImageResource(
            if (imeAction == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) R.drawable.ic_search
            else R.drawable.ic_enter,
        )
        // Rebuild the symbols page so recents reflect the last session. Recents never reshuffle
        // while the keyboard is open, so keys don't jump under a finger.
        if (tab == 0) selectTab(0)
    }

    // --- tabs ------------------------------------------------------------------------------------

    private fun selectTab(t: Int) {
        tab = t
        prefs.lastTab = t
        styleTab(tabSymbols, t == 0)
        styleTab(tabSearch, t == 1)
        hideAlternates()
        hint.text = ""
        content.removeAllViews()
        val page = if (t == 0) symbolsPage() else searchPage()
        content.addView(page, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun styleTab(v: TextView, selected: Boolean) {
        v.background = rounded(if (selected) palette.accent else palette.keySpecial, dp(15).toFloat(), ripple = true)
        v.setTextColor(if (selected) palette.onAccent else palette.text)
        v.isSelected = selected
    }

    private fun tabView(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        setOnClickListener { host.haptic(it); onClick() }
    }

    // --- symbols ---------------------------------------------------------------------------------

    private val gridListener = object : KeyGridView.Listener {
        override fun onKeyDown(sym: Sym) {
            hint.text = describe(sym)
        }

        override fun onKeyTap(sym: Sym) {
            host.haptic(this@PogoKeyboardView)
            host.commitSymbol(sym)
        }

        override fun onKeyLongPress(sym: Sym) {
            host.haptic(this@PogoKeyboardView)
            showAlternates(sym)
        }

        override fun onKeyRelease() {}
    }

    private fun describe(sym: Sym): String = buildString {
        append(sym.symbol)
        if (sym.name.isNotEmpty()) append("  ").append(sym.name)
        if (sym.alternates.isNotEmpty()) append("  · hold for ").append(sym.alternates.size).append(" more")
    }

    private fun recentSection(): KeyGridView.Section? {
        if (!prefs.showRecents) return null
        val items = prefs.recents.map { catalog.findSymbol(it) ?: Sym(it, "") }
        return if (items.isEmpty()) null else KeyGridView.Section("Recent", items, maxRows = 1)
    }

    private fun symbolsPage(): View {
        if (catalog.symbols.isEmpty()) return emptyNote("No symbols. Check the hidden groups in settings.")
        val grid = KeyGridView(context, palette, gridListener).apply { setPadding(dp(4), 0, dp(4), dp(4)) }
        return when (layoutMode) {
            Layout.COMPACT, Layout.CAPTIONED -> {
                grid.captions = layoutMode == Layout.CAPTIONED
                grid.sections = listOfNotNull(recentSection()) +
                    catalog.symbols.map { KeyGridView.Section(it.name, it.items, it.dense) }
                scroller(grid)
            }
            Layout.PAGED -> {
                val recent = recentSection()
                val pages = listOfNotNull(recent?.let { KeyGridView.Section(null, it.items.take(Prefs.MAX_RECENTS)) to "Recent" }) +
                    catalog.symbols.map { KeyGridView.Section(null, it.items, it.dense) to it.name }
                pagedPage(pages.map { it.second }) { i ->
                    grid.captions = true
                    grid.sections = listOf(pages[i].first)
                    grid
                }.also { grid.fillHeight = contentHeight - dp(CHIP_ROW_DP) }
            }
        }
    }

    // --- search strings --------------------------------------------------------------------------

    private fun searchPage(): View {
        val list = if (catalog.searches.isEmpty()) emptyNote("No search strings. Add your own in settings.") else searchList()
        return LinearLayout(context).apply {
            orientation = VERTICAL
            addView(opsStrip(), LayoutParams(LayoutParams.MATCH_PARENT, dp(38)))
            addView(list, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    /** Search syntax and digits, so editing a string (`cp2500-`, `4*`, `#tag`) doesn't need Gboard. */
    private fun opsStrip(): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        for (op in SEARCH_KEYS) {
            row.addView(textKey(op, special = true).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                contentDescription = OPERATOR_NAMES[op] ?: op
                setOnClickListener { host.haptic(it); host.commitRaw(op) }
            }, LayoutParams(dp(36), LayoutParams.MATCH_PARENT).apply { rightMargin = dp(4) })
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun searchList(): View {
        return when (layoutMode) {
            Layout.COMPACT -> scroller(LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(6), 0, dp(6), dp(6))
                for (g in catalog.searches) {
                    addView(groupHeader(g.name))
                    addView(FlowLayout(context, dp(6), dp(6)).apply {
                        for (e in g.items) addView(searchChip(e))
                    })
                }
            })
            Layout.CAPTIONED -> scroller(LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(6), 0, dp(6), dp(6))
                for (g in catalog.searches) {
                    addView(groupHeader(g.name))
                    for (e in g.items) addView(searchRow(e))
                }
            })
            Layout.PAGED -> pagedPage(catalog.searches.map { it.name }) { i ->
                scroller(LinearLayout(context).apply {
                    orientation = VERTICAL
                    setPadding(dp(6), dp(2), dp(6), dp(6))
                    for (e in catalog.searches[i].items) addView(searchRow(e))
                })
            }
        }
    }

    private fun searchChip(e: SearchEntry) = TextView(context).apply {
        text = e.name
        setTextColor(palette.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = resources.displayMetrics.widthPixels - dp(24)
        minHeight = dp(40)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), 0, dp(12), 0)
        background = rounded(palette.key, dp(8).toFloat(), ripple = true)
        contentDescription = "Insert search: ${e.name}"
        bindSearch(this, e)
    }

    private fun searchRow(e: SearchEntry) = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(12), dp(6), dp(12), dp(7))
        background = rounded(palette.key, dp(8).toFloat(), ripple = true)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(5) }
        addView(TextView(context).apply {
            text = e.name
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        addView(TextView(context).apply {
            text = e.string
            setTextColor(palette.subtext)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        contentDescription = "Insert search: ${e.name}"
        bindSearch(this, e)
    }

    private fun bindSearch(v: View, e: SearchEntry) {
        v.setOnClickListener { host.haptic(it); host.commitSearch(e) }
        v.setOnLongClickListener {
            host.haptic(it)
            showText(e.name, e.string)
            true
        }
    }

    // --- shared pieces ---------------------------------------------------------------------------

    /** Chip row of [names] above a page produced by [pageFor]; the selected page is remembered. */
    private fun pagedPage(names: List<String>, pageFor: (Int) -> View): View {
        val root = LinearLayout(context).apply { orientation = VERTICAL }
        val chips = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(6), 0)
        }
        val holder = FrameLayout(context)
        val chipViews = ArrayList<TextView>()
        fun select(i: Int) {
            prefs.lastPage = i
            chipViews.forEachIndexed { j, c ->
                c.background = rounded(if (j == i) palette.accent else palette.keySpecial, dp(13).toFloat(), ripple = true)
                c.setTextColor(if (j == i) palette.onAccent else palette.text)
            }
            holder.removeAllViews()
            holder.addView(pageFor(i), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            hint.text = ""
        }
        names.forEachIndexed { i, n ->
            val c = TextView(context).apply {
                text = n
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener { host.haptic(it); select(i) }
            }
            chipViews += c
            chips.addView(c, LayoutParams(LayoutParams.WRAP_CONTENT, dp(26)).apply { rightMargin = dp(5) })
        }
        root.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(CHIP_ROW_DP)))
        root.addView(holder, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        select(prefs.lastPage.coerceIn(0, names.size - 1))
        return root
    }

    private fun scroller(child: View) = ScrollView(context).apply {
        isVerticalScrollBarEnabled = true
        isScrollbarFadingEnabled = true
        overScrollMode = OVER_SCROLL_NEVER
        addView(child)
    }

    private fun groupHeader(name: String) = TextView(context).apply {
        text = name.uppercase()
        setTextColor(palette.subtext)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.06f
        setPadding(dp(4), dp(8), 0, dp(4))
    }

    private fun emptyNote(msg: String) = TextView(context).apply {
        text = msg
        setTextColor(palette.subtext)
        gravity = Gravity.CENTER
        setPadding(dp(24), 0, dp(24), 0)
    }

    // --- alternates strip ------------------------------------------------------------------------

    private fun showAlternates(sym: Sym) {
        altKeys.removeAllViews()
        altName.text = sym.name.ifEmpty { sym.symbol }
        for (s in listOf(sym.symbol) + sym.alternates) {
            altKeys.addView(TextView(context).apply {
                text = s
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(if (sym.color != null) android.graphics.Color.WHITE else palette.text)
                gravity = Gravity.CENTER
                background = rounded(sym.color ?: palette.key, dp(6).toFloat(), ripple = true)
                contentDescription = "${sym.name} $s"
                setOnClickListener {
                    host.haptic(it)
                    host.commitSymbol(Sym(s, sym.name, color = sym.color))
                    hideAlternates()
                }
            }, LayoutParams(dp(44), dp(34)).apply { rightMargin = dp(4) })
        }
        normalBar.visibility = INVISIBLE
        altBar.visibility = VISIBLE
    }

    /** Shows a full search string (long-press) in the bar, since rows truncate it. */
    private fun showText(title: String, body: String) {
        altKeys.removeAllViews()
        altName.text = title
        altKeys.addView(TextView(context).apply {
            text = body
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            maxLines = 1
            setPadding(dp(4), 0, dp(12), 0)
        })
        normalBar.visibility = INVISIBLE
        altBar.visibility = VISIBLE
    }

    private fun hideAlternates() {
        altBar.visibility = GONE
        normalBar.visibility = VISIBLE
    }

    // --- bottom row ------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun bottomRow(): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(3), dp(2), dp(3), dp(5))
        }
        fun add(v: View, weight: Float) {
            row.addView(v, LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply { leftMargin = dp(2); rightMargin = dp(2) })
        }

        val globe = iconKey(R.drawable.ic_globe, "Switch to previous keyboard. Long press to choose a keyboard.")
        globe.setOnClickListener { host.haptic(it); host.switchBack() }
        globe.setOnLongClickListener { host.haptic(it); host.showPicker(); true }
        add(globe, 1.3f)

        for (op in OPERATORS) add(textKey(op, special = true).apply {
            contentDescription = OPERATOR_NAMES[op]
            setOnClickListener { host.haptic(it); host.commitRaw(op) }
        }, 1f)

        val space = textKey("Pogo", special = false).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(palette.subtext)
            contentDescription = "Space. Long press to choose a keyboard."
            setOnClickListener { host.haptic(it); host.commitRaw(" ") }
            setOnLongClickListener { host.haptic(it); host.showPicker(); true }
        }
        add(space, 3.2f)

        val del = iconKey(R.drawable.ic_backspace, "Delete")
        del.setOnTouchListener(RepeatListener { host.haptic(del); host.deleteBackward() })
        add(del, 1.5f)

        val enter = iconKey(R.drawable.ic_enter, "Enter", accent = true)
        enter.setOnClickListener { host.haptic(it); host.enter() }
        enterIcon = enter
        add(enter, 1.5f)
        return row
    }

    private fun textKey(label: String, special: Boolean) = TextView(context).apply {
        text = label
        setTextColor(palette.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        gravity = Gravity.CENTER
        background = rounded(if (special) palette.keySpecial else palette.key, dp(6).toFloat(), ripple = true)
    }

    private fun iconKey(res: Int, desc: String, accent: Boolean = false) = ImageView(context).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(if (accent) palette.onAccent else palette.text)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = desc
        background = rounded(if (accent) palette.accent else palette.keySpecial, dp(6).toFloat(), ripple = true)
    }

    private fun iconButton(res: Int, desc: String, tint: Int, onClick: () -> Unit) = ImageView(context).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(tint)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = desc
        background = RippleDrawable(ColorStateList.valueOf(palette.keyPressed), null, null)
        setOnClickListener { host.haptic(it); onClick() }
    }

    private fun rounded(color: Int, radius: Float, ripple: Boolean): Drawable {
        val shape = GradientDrawable().apply { setColor(color); cornerRadius = radius }
        return if (ripple) RippleDrawable(ColorStateList.valueOf(palette.keyPressed), shape, null) else shape
    }

    /** Fires once on press, then repeats while held (backspace). */
    private class RepeatListener(private val action: () -> Unit) : View.OnTouchListener {
        private var view: View? = null
        private var count = 0
        private val repeat = object : Runnable {
            override fun run() {
                val v = view ?: return
                // A press can outlive the keyboard (IME switched while held); never type into the next field.
                if (!v.isAttachedToWindow || !v.isPressed) { view = null; return }
                action()
                count++
                v.postDelayed(this, if (count > 12) 35L else 70L)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view = v
                    count = 0
                    v.isPressed = true
                    action()
                    v.postDelayed(repeat, 400L)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(repeat)
                    v.isPressed = false
                    view = null
                }
            }
            return true
        }
    }

    // Must stay the last initializer: building a page uses listeners declared above.
    init {
        selectTab(tab)
    }

    companion object {
        private const val CHIP_ROW_DP = 34
        private val OPERATORS = listOf("&", ",", "!", "-")
        private val SEARCH_KEYS = listOf("#", "@", "+", "*", ":", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        private val OPERATOR_NAMES = mapOf(
            "&" to "and", "," to "comma, or", "!" to "not", "-" to "dash, range",
            "#" to "tag", "@" to "move", "+" to "family", "*" to "star", ":" to "colon, or",
        )
    }
}
