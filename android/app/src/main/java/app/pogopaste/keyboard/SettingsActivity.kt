package app.pogopaste.keyboard

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * Setup + settings in one scrolling screen, built in code from platform widgets (no AppCompat,
 * no Preference library) to keep the APK tiny. Every control writes straight to [Prefs]; the
 * keyboard notices structural changes and rebuilds itself the next time it opens.
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var col: LinearLayout
    private lateinit var enableStatus: TextView
    private lateinit var switchStatus: TextView
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(col)
            // targetSdk 35+ is edge-to-edge on Android 15+: keep content clear of system bars.
            setOnApplyWindowInsetsListener { v, insets ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        setContentView(scroll)
        build()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The IME picker is a dialog over us; refresh when it closes.
        if (hasFocus) refreshStatus()
    }

    private fun build() {
        col.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
        })
        col.addView(body("Unicode symbols and search strings for naming and filtering, one tap away from your normal keyboard."))

        // --- setup ---
        section("Set up")
        enableStatus = body("")
        col.addView(stepRow("1. Turn on Pogo Keyboard", enableStatus, "Open settings") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        switchStatus = body("")
        col.addView(stepRow("2. Make it the current keyboard", switchStatus, "Choose") {
            imm().showInputMethodPicker()
        })
        col.addView(EditText(this).apply {
            hint = "Try it here…"
            setSingleLine()
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }, lp(top = 8))

        section("Switching with Gboard")
        col.addView(body(
            "• In Pogo: tap 🌐 to jump straight back to Gboard. Long-press 🌐 or the space bar to pick any keyboard.\n" +
                "• In Gboard: once Pogo is turned on, Gboard shows a 🌐 key (Gboard settings › Preferences › Show language switch key). Tap it to cycle to Pogo, or long-press it to pick.\n" +
                "• Anywhere: the keyboard icon in the navigation bar (3-button navigation) opens the picker.\n" +
                "• Turn on \"Return after inserting\" below to bounce back to Gboard automatically after you tap a search string.",
        ))

        // --- layout ---
        section("Layout")
        val layoutGroup = RadioGroup(this)
        val layouts = listOf(
            Layout.COMPACT to ("Compact grid" to "Every symbol in one dense, scrollable grid. Most keys on screen; press a key to see its name."),
            Layout.CAPTIONED to ("Captioned" to "Larger keys with the description under each symbol, like the website."),
            Layout.PAGED to ("Paged groups" to "One group at a time (Types, Stats, …) picked from a chip row. Keys stretch to fill, so most groups need no scrolling."),
        )
        for ((mode, label) in layouts) {
            layoutGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = "${label.first}\n${label.second}"
                isChecked = prefs.layout == mode
                setPadding(dp(8), dp(8), 0, dp(8))
                setOnCheckedChangeListener { _, checked -> if (checked) prefs.layout = mode }
            })
        }
        col.addView(layoutGroup)

        val heightLabel = body("")
        fun showHeight(v: Int) { heightLabel.text = "Keyboard height: $v dp" }
        showHeight(prefs.heightDp)
        col.addView(heightLabel, lp(top = 8))
        col.addView(SeekBar(this).apply {
            max = (Prefs.MAX_HEIGHT_DP - Prefs.MIN_HEIGHT_DP) / 4
            progress = (prefs.heightDp - Prefs.MIN_HEIGHT_DP) / 4
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    showHeight(Prefs.MIN_HEIGHT_DP + p * 4)
                    // Save every user change: D-pad, keyboard and TalkBack adjustments never
                    // produce a "stop tracking" callback. apply() is async and cheap.
                    if (fromUser) prefs.heightDp = Prefs.MIN_HEIGHT_DP + p * 4
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        })

        // --- behavior ---
        section("Behavior")
        col.addView(toggle("Vibrate on key press", "Follows the system touch-feedback setting too.", prefs.haptics) { prefs.haptics = it })
        col.addView(toggle("Combine search strings with &", "Tapping a search string after another one joins them, so both must match.", prefs.joinSearches) { prefs.joinSearches = it })
        col.addView(toggle("Show recent symbols", "A row of your last-used symbols at the top.", prefs.showRecents) { prefs.showRecents = it })
        col.addView(body("After inserting, return to the previous keyboard:"), lp(top = 12))
        val ret = RadioGroup(this)
        for ((mode, label) in listOf(
            ReturnMode.NEVER to "Never — stay on Pogo",
            ReturnMode.AFTER_SEARCH to "After a search string",
            ReturnMode.ALWAYS to "After any symbol or search string",
        )) {
            ret.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                isChecked = prefs.returnMode == mode
                setOnCheckedChangeListener { _, checked -> if (checked) prefs.returnMode = mode }
            })
        }
        col.addView(ret)

        // --- groups ---
        section("Groups")
        col.addView(body("Uncheck a group to hide it from the keyboard."))
        val base = PogoImeService.loadCatalog(this)
        for (g in base.symbols) col.addView(groupBox("Symbols · ${g.name}", "sym:${g.name}"))
        for (g in base.searches) col.addView(groupBox("Search · ${g.name}", "search:${g.name}"))

        // --- user content ---
        section("Your symbols")
        col.addView(body("One per line: the symbol, then an optional name. Several bare symbols on one line are added without names. Shown first as \"Mine\"."))
        col.addView(editor(prefs.userSymbols, "✨ Shiny\n♛ Best buddy\n★ ☆ ✪") { prefs.userSymbols = it })

        section("Your search strings")
        col.addView(body("One per line: Name | search string. Shown first as \"Mine\"."))
        col.addView(editor(prefs.userSearches, "Raid attackers | 4*,3*&cp2500-\nTrade fodder | !4*&!shiny&!legendary&distance100-") { prefs.userSearches = it })

        // --- reset ---
        section("Reset")
        col.addView(Button(this).apply {
            text = "Clear recent symbols"
            setOnClickListener { prefs.recents = emptyList(); toast("Recents cleared") }
        }, lp(top = 4))
        col.addView(Button(this).apply {
            text = "Reset layout and behavior"
            setOnClickListener { prefs.resetAppearance(); recreate() }
        }, lp(top = 4))

        col.addView(TextView(this).apply {
            text = "Pogo Keyboard ${BuildConfig.VERSION_NAME} · pogo-paste.netlify.app\nNo permissions. Nothing you type leaves your phone."
            setTextColor(subtextColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(24), 0, 0)
            setOnClickListener {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pogo-paste.netlify.app/"))) }
            }
        })
    }

    private fun refreshStatus() {
        if (!::enableStatus.isInitialized) return
        val enabled = isEnabled()
        val current = isCurrent()
        enableStatus.text = if (enabled) "✓ Turned on" else "Turn on \"${getString(R.string.ime_name)}\" in the list of keyboards."
        switchStatus.text = when {
            current -> "✓ Pogo is the current keyboard"
            enabled -> "Pick Pogo Keyboard in the chooser."
            else -> "Do step 1 first."
        }
    }

    private fun ourId(): String = ComponentName(this, PogoImeService::class.java).flattenToShortString()

    private fun isEnabled(): Boolean {
        val full = ComponentName(this, PogoImeService::class.java)
        return imm().enabledInputMethodList.any { it.component == full }
    }

    private fun isCurrent(): Boolean {
        val cur = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
        return ComponentName.unflattenFromString(cur) == ComponentName(this, PogoImeService::class.java) || cur == ourId()
    }

    private fun imm() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    // --- little view helpers ---------------------------------------------------------------

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    private fun subtextColor(): Int {
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.textColorSecondary, tv, true)
        return if (tv.resourceId != 0) getColor(tv.resourceId) else tv.data
    }

    private fun section(title: String) {
        col.addView(TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
        }, lp(top = 24))
    }

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(subtextColor())
        setPadding(0, dp(4), 0, 0)
    }

    private fun stepRow(title: String, status: TextView, action: String, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = title
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                })
                addView(status)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(context).apply {
                text = action
                setOnClickListener { onClick() }
            })
        }

    @Suppress("DEPRECATION") // android.widget.Switch: fine without AppCompat.
    private fun toggle(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) =
        Switch(this).apply {
            text = "$title\n$sub"
            isChecked = value
            setPadding(0, dp(10), 0, dp(10))
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }

    private fun groupBox(label: String, key: String) = CheckBox(this).apply {
        text = label
        isChecked = key !in prefs.hidden
        setOnCheckedChangeListener { _, checked ->
            prefs.hidden = if (checked) prefs.hidden - key else prefs.hidden + key
        }
    }

    private fun editor(value: String, hintText: String, onChange: (String) -> Unit) = EditText(this).apply {
        setText(value)
        hint = hintText
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        minLines = 3
        gravity = Gravity.TOP or Gravity.START
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), subtextColor())
        }
        setPadding(dp(12), dp(10), dp(12), dp(10))
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: "") }
        })
    }.also { it.layoutParams = lp(top = 8) }

    private fun toast(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
}
