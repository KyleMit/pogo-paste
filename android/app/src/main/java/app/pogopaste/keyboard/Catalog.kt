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
                } else {
                    val first = graphemes(parts[0]).firstOrNull() ?: continue
                    val rest = line.substring(line.indexOf(first) + first.length).trim()
                    if (seen.add(first)) out += Sym(first, rest)
                }
            }
            return out
        }

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

        /** Splits text into user-perceived characters (handles surrogate pairs and combining marks). */
        fun graphemes(text: String): List<String> {
            if (text.isEmpty()) return emptyList()
            val it = BreakIterator.getCharacterInstance()
            it.setText(text)
            val out = ArrayList<String>()
            var start = it.first()
            var end = it.next()
            while (end != BreakIterator.DONE) {
                val g = text.substring(start, end)
                if (g.isNotBlank()) out += g
                start = end
                end = it.next()
            }
            return out
        }
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
