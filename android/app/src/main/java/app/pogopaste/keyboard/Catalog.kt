package app.pogopaste.keyboard

import org.json.JSONArray
import java.text.BreakIterator

/** One tappable symbol. [alternates] are already split into user-perceived characters. */
data class Sym(
    val symbol: String,
    val name: String,
    val alternates: List<String> = emptyList(),
    val color: Int? = null,
)

data class SymGroup(val name: String, val items: List<Sym>, val dense: Boolean = false)

data class SearchEntry(val name: String, val string: String)

data class SearchGroup(val name: String, val items: List<SearchEntry>)

data class Catalog(val symbols: List<SymGroup>, val searches: List<SearchGroup>) {
    companion object {
        const val MINE = "Mine"

        /** Type colors mirrored from the website's style.css (`--type-*`). */
        private val TYPE_COLORS = mapOf(
            "type-bug" to 0xFF85B430, "type-dark" to 0xFF4F4C5F, "type-dragon" to 0xFF035094,
            "type-electric" to 0xFFC09B2D, "type-fairy" to 0xFFC966BF, "type-fighting" to 0xFFA02C3B,
            "type-fire" to 0xFFBE7510, "type-flying" to 0xFF7188B0, "type-ghost" to 0xFF574E87,
            "type-grass" to 0xFF248B46, "type-ground" to 0xFFAA6641, "type-ice" to 0xFF6EA19F,
            "type-normal" to 0xFF727475, "type-poison" to 0xFFB03A9A, "type-psychic" to 0xFFCC595D,
            "type-rock" to 0xFF9F9052, "type-steel" to 0xFF548A9E, "type-water" to 0xFF3F7DBA,
        ).mapValues { it.value.toInt() }

        /** Groups whose names add nothing over the glyph itself ("One" for ①) render without captions. */
        private val DENSE_GROUPS = setOf("Numbers")

        /**
         * Parses the website's `symbols.json` and `search.json`. Malformed entries are skipped rather
         * than failing the whole keyboard: a keyboard that won't open is far worse than a missing key.
         */
        fun parse(symbolsJson: String, searchJson: String): Catalog =
            Catalog(parseSymbols(symbolsJson), parseSearches(searchJson))

        fun parseSymbols(json: String): List<SymGroup> {
            val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
            val groups = ArrayList<SymGroup>(arr.length())
            for (i in 0 until arr.length()) {
                val g = arr.optJSONObject(i) ?: continue
                val name = g.optString("name").trim()
                val items = g.optJSONArray("items") ?: continue
                val syms = ArrayList<Sym>(items.length())
                for (j in 0 until items.length()) {
                    val it = items.optJSONObject(j) ?: continue
                    val symbol = it.optString("symbol").trim()
                    if (symbol.isEmpty()) continue
                    syms += Sym(
                        symbol = symbol,
                        name = it.optString("name").trim(),
                        alternates = graphemes(it.optString("alternates")).filter { a -> a != symbol && a.isNotBlank() },
                        color = TYPE_COLORS[it.optString("className")],
                    )
                }
                if (syms.isNotEmpty()) groups += SymGroup(name, syms, dense = name in DENSE_GROUPS)
            }
            return groups
        }

        fun parseSearches(json: String): List<SearchGroup> {
            val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
            val groups = ArrayList<SearchGroup>(arr.length())
            for (i in 0 until arr.length()) {
                val g = arr.optJSONObject(i) ?: continue
                val items = g.optJSONArray("items") ?: continue
                val entries = ArrayList<SearchEntry>(items.length())
                for (j in 0 until items.length()) {
                    val it = items.optJSONObject(j) ?: continue
                    val s = it.optString("string").trim()
                    if (s.isEmpty()) continue
                    entries += SearchEntry(it.optString("name").trim().ifEmpty { s }, s)
                }
                if (entries.isNotEmpty()) groups += SearchGroup(g.optString("name").trim(), entries)
            }
            return groups
        }

        /**
         * User symbols, one per line: the first character is the symbol, an optional
         * description follows (`✨ Shiny`). A line can hold several bare symbols (`★ ☆ ✪`) to add
         * them all without names.
         */
        fun parseUserSymbols(text: String): List<Sym> {
            val out = ArrayList<Sym>()
            val seen = HashSet<String>()
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("//")) continue
                val parts = line.split(Regex("\\s+"))
                val allSingles = parts.all { graphemes(it).size == 1 }
                if (allSingles && parts.size > 1) {
                    for (p in parts) if (seen.add(p)) out += Sym(p, "")
                } else if (parts.size > 1 && isWordy(parts[0]) && graphemes(parts[0]).size > 1 &&
                    graphemes(parts.last()).size == 1 && !isWordy(parts.last())
                ) {
                    // Name written first ("Shiny ✨"): take the trailing symbol rather than the "S".
                    // A single-character first token ("1 Rank ★") stays symbol-first.
                    val sym = parts.last()
                    val name = line.substring(0, line.lastIndexOf(sym)).trim()
                    if (seen.add(sym)) out += Sym(sym, name)
                } else {
                    val first = graphemes(parts[0]).firstOrNull() ?: continue
                    val rest = line.substring(line.indexOf(first) + first.length).trim()
                    if (seen.add(first)) out += Sym(first, rest)
                }
            }
            return out
        }

        /** True for plain ASCII words/numbers ("Shiny", "100"), i.e. a name rather than a symbol. */
        private fun isWordy(token: String) = token.isNotEmpty() && token.all { it.code < 128 && it.isLetterOrDigit() }

        /** User search strings, one per line: `Name | string`, or just `string`. */
        fun parseUserSearches(text: String): List<SearchEntry> =
            text.lineSequence().mapNotNull { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("//")) return@mapNotNull null
                val bar = line.indexOf('|')
                if (bar < 0) SearchEntry(line, line)
                else {
                    val s = line.substring(bar + 1).trim()
                    if (s.isEmpty()) null else SearchEntry(line.substring(0, bar).trim().ifEmpty { s }, s)
                }
            }.toList()

        /**
         * Splits text into user-perceived characters (surrogate pairs, combining marks, emoji
         * sequences). Blank clusters are dropped.
         */
        fun graphemes(text: String): List<String> = clusters(text).filter { it.isNotBlank() }

        /**
         * Every grapheme cluster in [text], blanks included. The platform BreakIterator is the base;
         * on older runtimes (Android 7's ICU) it splits skin tones and ZWJ sequences (👍🏽 → 👍 + 🏽),
         * so [mergeEmojiClusters] rejoins those. On current runtimes the merge is a no-op.
         */
        fun clusters(text: String): List<String> {
            if (text.isEmpty()) return emptyList()
            val it = BreakIterator.getCharacterInstance()
            it.setText(text)
            val out = ArrayList<String>()
            var start = it.first()
            var end = it.next()
            while (end != BreakIterator.DONE) {
                out += text.substring(start, end)
                start = end
                end = it.next()
            }
            return mergeEmojiClusters(out)
        }

        /**
         * Glues emoji continuation pieces onto the preceding cluster. A ZWJ only joins two
         * pictographs (👩 ZWJ 💻), as in Unicode's GB11 rule; ZWJ between letters ("a‍b", Arabic
         * shaping) keeps the platform's boundary so backspace still deletes one letter.
         */
        fun mergeEmojiClusters(parts: List<String>): List<String> {
            val out = ArrayList<String>(parts.size)
            for (p in parts) {
                val prev = out.lastOrNull()
                val joinsZwj = prev != null && prev.codePointBefore(prev.length) == ZWJ &&
                    isPictographic(prev.codePointAt(0)) && isPictographic(p.codePointAt(0))
                if (prev != null && (continuesCluster(p.codePointAt(0)) || joinsZwj)) {
                    out[out.size - 1] = prev + p
                } else {
                    out += p
                }
            }
            return out
        }

        /** Close-enough Extended_Pictographic check for the blocks emoji live in. */
        private fun isPictographic(cp: Int) =
            cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2300..0x23FF ||
                cp in 0x2B00..0x2BFF || cp in 0x2190..0x21FF || cp == 0x00A9 || cp == 0x00AE ||
                cp == 0x203C || cp == 0x2049 || cp == 0x2122 || cp == 0x2139 || cp == 0x3030 || cp == 0x303D

        private const val ZWJ = 0x200D

        private fun continuesCluster(cp: Int) =
            cp == ZWJ ||
                cp in 0xFE00..0xFE0F || // variation selectors (emoji / text presentation)
                cp in 0x1F3FB..0x1F3FF || // skin-tone modifiers
                cp in 0xE0020..0xE007F || // tag sequences (subdivision flags)
                cp == 0x20E3 // combining enclosing keycap
    }

    /** Applies user customization: a "Mine" group first, hidden groups removed. */
    fun customized(userSymbols: List<Sym>, userSearches: List<SearchEntry>, hidden: Set<String>): Catalog {
        val syms = buildList {
            if (userSymbols.isNotEmpty()) add(SymGroup(MINE, userSymbols))
            symbols.filterTo(this) { "sym:${it.name}" !in hidden }
        }
        val searches = buildList {
            if (userSearches.isNotEmpty()) add(SearchGroup(MINE, userSearches))
            searches.filterTo(this) { "search:${it.name}" !in hidden }
        }
        return Catalog(syms, searches)
    }

    /** Lookup used to give recents their names and colors back. */
    fun findSymbol(s: String): Sym? {
        for (g in symbols) for (it in g.items) {
            if (it.symbol == s) return it
        }
        for (g in symbols) for (it in g.items) {
            if (s in it.alternates) return Sym(s, it.name, color = it.color)
        }
        return null
    }
}
