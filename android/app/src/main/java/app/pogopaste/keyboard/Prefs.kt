package app.pogopaste.keyboard

import android.content.Context
import android.content.SharedPreferences

enum class Layout(val key: String) {
    /** Every group flows into one dense, scrollable grid of bare glyphs. */
    COMPACT("compact"),
    /** Fewer, larger keys with the description printed under each glyph. */
    CAPTIONED("captioned"),
    /** One group at a time, picked from a chip row — no scrolling. */
    PAGED("paged");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: COMPACT
    }
}

enum class ReturnMode(val key: String) {
    NEVER("never"), AFTER_SEARCH("search"), ALWAYS("always");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: NEVER
    }
}

/** Thin typed wrapper over SharedPreferences. Every read has a safe default. */
class Prefs(context: Context) {
    val sp: SharedPreferences = context.applicationContext
        .getSharedPreferences("pogo_keyboard", Context.MODE_PRIVATE)

    var layout: Layout
        get() = Layout.of(sp.getString(K_LAYOUT, null))
        set(v) = sp.edit().putString(K_LAYOUT, v.key).apply()

    /** Height of the content area (excluding the top bar and bottom row), in dp. */
    var heightDp: Int
        get() = sp.getInt(K_HEIGHT, DEFAULT_HEIGHT_DP).coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
        set(v) = sp.edit().putInt(K_HEIGHT, v.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)).apply()

    var haptics: Boolean
        get() = sp.getBoolean(K_HAPTICS, true)
        set(v) = sp.edit().putBoolean(K_HAPTICS, v).apply()

    var returnMode: ReturnMode
        get() = ReturnMode.of(sp.getString(K_RETURN, null))
        set(v) = sp.edit().putString(K_RETURN, v.key).apply()

    /** Insert `&` before a search string when the field already has a search in it. */
    var joinSearches: Boolean
        get() = sp.getBoolean(K_JOIN, true)
        set(v) = sp.edit().putBoolean(K_JOIN, v).apply()

    var showRecents: Boolean
        get() = sp.getBoolean(K_SHOW_RECENTS, true)
        set(v) = sp.edit().putBoolean(K_SHOW_RECENTS, v).apply()

    var userSymbols: String
        get() = sp.getString(K_USER_SYMBOLS, "") ?: ""
        set(v) = sp.edit().putString(K_USER_SYMBOLS, v).apply()

    var userSearches: String
        get() = sp.getString(K_USER_SEARCHES, "") ?: ""
        set(v) = sp.edit().putString(K_USER_SEARCHES, v).apply()

    /** Entries look like `sym:Types` / `search:IVs`. */
    var hidden: Set<String>
        get() = sp.getStringSet(K_HIDDEN, emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet(K_HIDDEN, v).apply()

    var lastTab: Int
        get() = sp.getInt(K_TAB, 0)
        set(v) = sp.edit().putInt(K_TAB, v).apply()

    var lastPage: Int
        get() = sp.getInt(K_PAGE, 0)
        set(v) = sp.edit().putInt(K_PAGE, v).apply()

    /** Most recent first, newline separated (symbols never contain newlines). */
    var recents: List<String>
        get() = (sp.getString(K_RECENTS, "") ?: "").split('\n').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString(K_RECENTS, v.take(MAX_RECENTS).joinToString("\n")).apply()

    fun pushRecent(symbol: String) {
        if (symbol.isEmpty() || '\n' in symbol) return
        val r = recents
        if (r.firstOrNull() == symbol) return
        recents = listOf(symbol) + r.filter { it != symbol }
    }

    fun resetAppearance() {
        sp.edit().remove(K_LAYOUT).remove(K_HEIGHT).remove(K_HAPTICS).remove(K_RETURN)
            .remove(K_SHOW_RECENTS).remove(K_JOIN).remove(K_HIDDEN).apply()
    }

    companion object {
        const val DEFAULT_HEIGHT_DP = 216
        const val MIN_HEIGHT_DP = 140
        const val MAX_HEIGHT_DP = 360
        const val MAX_RECENTS = 24

        private const val K_LAYOUT = "layout"
        private const val K_HEIGHT = "height_dp"
        private const val K_HAPTICS = "haptics"
        private const val K_RETURN = "return_mode"
        private const val K_SHOW_RECENTS = "show_recents"
        private const val K_JOIN = "join_searches"
        private const val K_USER_SYMBOLS = "user_symbols"
        private const val K_USER_SEARCHES = "user_searches"
        private const val K_HIDDEN = "hidden_groups"
        private const val K_TAB = "last_tab"
        private const val K_PAGE = "last_page"
        private const val K_RECENTS = "recents"

        /** Keys whose change requires rebuilding the keyboard view. Tab/page/recents don't. */
        val STRUCTURAL = setOf(K_LAYOUT, K_HEIGHT, K_SHOW_RECENTS, K_USER_SYMBOLS, K_USER_SEARCHES, K_HIDDEN)
    }
}
