package app.pogopaste.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogTest {

    private fun data(name: String) = File("../../src/_data/$name").readText()

    @Test
    fun `parses the website data files`() {
        val c = Catalog.parse(data("symbols.json"), data("search.json"))
        assertTrue(c.symbols.size >= 5)
        assertTrue(c.searches.isNotEmpty())
        val types = c.symbols.first { it.name == "Types" }
        assertEquals(18, types.items.size)
        val fire = types.items.first { it.name == "Fire" }
        assertEquals("♨", fire.symbol)
        assertEquals(listOf("㊋"), fire.alternates)
        assertTrue(fire.color != null)
        assertTrue(c.symbols.first { it.name == "Numbers" }.dense)
        // Every symbol in the data must be a single user-perceived character, or the keys break.
        for (g in c.symbols) for (s in g.items) {
            assertEquals("${g.name}/${s.name} '${s.symbol}'", 1, Catalog.graphemes(s.symbol).size)
        }
    }

    @Test
    fun `alternates split by grapheme, including astral and combining characters`() {
        assertEquals(listOf("⁂", "ප"), Catalog.graphemes("⁂ප"))
        assertEquals(listOf("☇", "☈", "⚡"), Catalog.graphemes("☇☈⚡"))
        assertEquals(listOf("😀", "é"), Catalog.graphemes("😀é"))
        assertEquals(emptyList<String>(), Catalog.graphemes(""))
    }

    @Test
    fun `malformed json yields empty groups instead of crashing`() {
        assertEquals(emptyList<SymGroup>(), Catalog.parseSymbols("not json"))
        assertEquals(emptyList<SearchGroup>(), Catalog.parseSearches("{}"))
        val partial = Catalog.parseSymbols("""[{"name":"A","items":[{"symbol":""},{"symbol":"x","name":"X"}, 5]}, "junk"]""")
        assertEquals(1, partial.size)
        assertEquals("x", partial[0].items.single().symbol)
    }

    @Test
    fun `user symbols accept names, bare lists, comments and dedupe`() {
        val s = Catalog.parseUserSymbols("✨ Shiny\n\n// comment\n★ ☆ ✪\n✨ again\n🔥 Hot stuff")
        assertEquals(listOf("✨", "★", "☆", "✪", "🔥"), s.map { it.symbol })
        assertEquals("Shiny", s[0].name)
        assertEquals("Hot stuff", s[4].name)
    }

    @Test
    fun `user searches accept name pipe string or bare string`() {
        val s = Catalog.parseUserSearches("Raid | 4*,3*&cp2500-\nshiny&!traded\nEmpty |  \n | nameless")
        assertEquals(3, s.size)
        assertEquals(SearchEntry("Raid", "4*,3*&cp2500-"), s[0])
        assertEquals(SearchEntry("shiny&!traded", "shiny&!traded"), s[1])
        assertEquals(SearchEntry("nameless", "nameless"), s[2])
    }

    @Test
    fun `customized puts Mine first and hides groups`() {
        val c = Catalog.parse(data("symbols.json"), data("search.json"))
        val out = c.customized(listOf(Sym("✨", "Shiny")), emptyList(), setOf("sym:Types", "search:IVs"))
        assertEquals(Catalog.MINE, out.symbols.first().name)
        assertTrue(out.symbols.none { it.name == "Types" })
        assertTrue(out.searches.none { it.name == "IVs" })
        assertEquals(c.searches.size - 1, out.searches.size)
    }

    @Test
    fun `findSymbol resolves primaries and alternates`() {
        val c = Catalog.parse(data("symbols.json"), data("search.json"))
        assertEquals("Fire", c.findSymbol("♨")?.name)
        assertEquals("Fire", c.findSymbol("㊋")?.name)
        assertNull(c.findSymbol("Q"))
    }

    @Test
    fun `last grapheme length handles surrogates and combining marks`() {
        assertEquals(1, PogoImeService.lastGraphemeLength("ab"))
        assertEquals(2, PogoImeService.lastGraphemeLength("a😀"))
        assertEquals(2, PogoImeService.lastGraphemeLength("xé"))
        assertEquals(0, PogoImeService.lastGraphemeLength(""))
    }
}

class JoinTest {
    @Test
    fun `joins with ampersand only after a term`() {
        assertEquals("", PogoImeService.joinPrefix(null))
        assertEquals("", PogoImeService.joinPrefix(""))
        assertEquals("&", PogoImeService.joinPrefix("4*"))
        assertEquals("", PogoImeService.joinPrefix("shiny&"))
        assertEquals("", PogoImeService.joinPrefix("shiny,"))
        assertEquals("", PogoImeService.joinPrefix("!"))
        assertEquals("", PogoImeService.joinPrefix("a "))
    }
}
