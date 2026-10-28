package app.pogopaste.keyboard

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import java.text.BreakIterator

class PogoImeService : InputMethodService(), PogoKeyboardView.Host {

    private lateinit var prefs: Prefs
    private var keyboard: PogoKeyboardView? = null
    private var dirty = false

    private val baseCatalog: Catalog get() = loadCatalog(this)

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in Prefs.STRUCTURAL) dirty = true
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        keyboard = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val start = System.nanoTime()
        dirty = false
        val catalog = baseCatalog.customized(
            Catalog.parseUserSymbols(prefs.userSymbols),
            Catalog.parseUserSearches(prefs.userSearches),
            prefs.hidden,
        )
        return PogoKeyboardView(this, this, catalog, prefs).also {
            keyboard = it
            if (BuildConfig.DEBUG) Log.d(TAG, "onCreateInputView ${(System.nanoTime() - start) / 1000} µs")
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Settings changed while we were hidden: rebuild. Not on a restart mid-typing, to avoid flicker.
        if (dirty && !restarting) setInputView(onCreateInputView())
        keyboard?.onStartInput(info.imeOptions and EditorInfo.IME_MASK_ACTION)
    }

    /** Never take over the whole screen in landscape: the user needs to see the field. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    // --- Host --------------------------------------------------------------------------------

    override fun commitSymbol(sym: Sym) {
        if (commit(sym.symbol)) {
            prefs.pushRecent(sym.symbol)
            if (prefs.returnMode == ReturnMode.ALWAYS) switchBack()
        }
    }

    override fun commitSearch(entry: SearchEntry) {
        val ic = currentInputConnection ?: return
        val text = if (prefs.joinSearches) joinPrefix(ic.getTextBeforeCursor(1, 0)) + entry.string else entry.string
        if (commit(text) && prefs.returnMode != ReturnMode.NEVER) switchBack()
    }

    override fun commitRaw(text: String) {
        commit(text)
    }

    private fun commit(text: String): Boolean {
        val ic = currentInputConnection ?: return false
        return ic.commitText(text, 1)
    }

    override fun deleteBackward() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(MAX_GRAPHEME_CHARS, 0)
        if (before.isNullOrEmpty()) {
            // Editors that don't expose their text (some game engines) still honor a DEL key event.
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        ic.deleteSurroundingText(lastGraphemeLength(before), 0)
    }

    override fun enter() {
        val ei = currentInputEditorInfo
        val ic = currentInputConnection ?: return
        val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
        val noAction = ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val multiLine = ei.inputType and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE != 0
        when {
            !noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED ->
                ic.performEditorAction(action)
            multiLine -> ic.commitText("\n", 1)
            else -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    override fun switchBack() {
        val switched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // "Previous" is empty right after boot or an `adb ime set`; with two keyboards "next" is the other one.
            switchToPreviousInputMethod() || switchToNextInputMethod(false)
        } else {
            val token = window?.window?.attributes?.token
            @Suppress("DEPRECATION")
            token != null && imm().switchToLastInputMethod(token)
        }
        // No previous keyboard recorded (e.g. Pogo was the first one used since boot): let the user pick.
        if (!switched) showPicker()
    }

    override fun showPicker() {
        imm().showInputMethodPicker()
    }

    override fun openSettings() {
        requestHideSelf(0)
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    override fun haptic(v: View) {
        if (prefs.haptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun imm() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    companion object {
        private const val TAG = "PogoIme"

        /** Enough context to find the last grapheme even for long ZWJ emoji sequences. */
        private const val MAX_GRAPHEME_CHARS = 32

        @Volatile private var cachedCatalog: Catalog? = null

        /**
         * The bundled data, parsed once per process and shared by the keyboard and the settings
         * screen. It's ~7 KB of immutable assets, so the cache never needs invalidating.
         */
        fun loadCatalog(context: Context): Catalog {
            cachedCatalog?.let { return it }
            fun read(name: String) = runCatching {
                context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrDefault("[]")
            return Catalog.parse(read("symbols.json"), read("search.json")).also { cachedCatalog = it }
        }

        /**
         * In Pokémon GO search, `&` (AND) binds loosest, so `existing&new` always means "both",
         * whatever commas the parts contain. Joins only when the cursor follows a term.
         */
        fun joinPrefix(before: CharSequence?): String {
            val last = before?.lastOrNull() ?: return ""
            return if (last.isWhitespace() || last in "&,;:|!") "" else "&"
        }

        /** Length in UTF-16 units of the last user-perceived character in [text]. */
        fun lastGraphemeLength(text: CharSequence): Int {
            if (text.isEmpty()) return 0
            val s = text.toString()
            val it = BreakIterator.getCharacterInstance()
            it.setText(s)
            val end = it.last()
            val start = it.previous()
            return if (start == BreakIterator.DONE) s.length else end - start
        }
    }
}
