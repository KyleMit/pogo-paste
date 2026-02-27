# Pogo Paste

<https://pogo-paste.netlify.app/>

Sets up easily copyable characters to use when naming pokemon or using long filter strings


## Dev Overview

**Stack:** [Eleventy](https://www.11ty.dev/) static site generator, Nunjucks templates, vanilla JS, plain CSS. No build pipeline beyond Eleventy itself.

### Structure

```none
src/
  index.njk           # single page entry point
  _data/
    symbols.json      # symbol groups and characters
    search.json       # search string entries
  _includes/
    views/            # toolbar, symbols, and search section templates
    scripts/          # toggle.js, copy.js (inlined at build time)
    styles/           # style.css (inlined at build time)
    icons/            # SVG icons (included inline via {% include %})
  static/             # passthrough-copied assets (images)
```

### Dev Notes

* Eleventy builds a single `index.html`. 
* JS and CSS are inlined via `{% include %}`
* No external requests except the Google Fonts import.
* Content lives in `_data/*.json` and is looped over in the Nunjucks templates.
* UI state (section and view mode) is managed by toggle script that
  * persists to `localStorage` and
  * applies classes to `<body>` which are used CSS selectors use to show/hide and style sections.

## Prior Art

* Docs
  * [Pokémon GO Search Bar Strings Cheat Sheet | Pokémon GO Hub](https://pokemongohub.net/post/guide/pokemon-go-search-bar-cheat-sheet/)
  * [Searching & Filtering your Pokémon Inventory — Pokémon GO Help Center](https://niantic.helpshift.com/hc/en/6-pokemon-go/faq/1486-searching-filtering-your-pokemon-inventory/)
* Symbols
  * [⚡⚡Unicode, Pokemon Go, and You! ༼ つ ◕_◕ ༽つ](https://www.reddit.com/r/pokemongo/comments/4xvh2q)
  * [Emojis and symbols that work on Pokemon Go](https://www.reddit.com/r/TheSilphRoad/comments/9onack)
  * [Now that some symbols have become unavailable when naming Pokémon, what other symbols will you be using instead?](https://www.reddit.com/r/TheSilphRoad/comments/mp19o6)
  * [Updated list of ASCII characters that work in nicknames?](https://www.reddit.com/r/TheSilphRoad/comments/8275y8)
  * [Here are some symbols you can copy and paste to label 2016, 2017, and traded Pokemon. ⇆ ⓰ ⓱](https://www.reddit.com/r/TheSilphRoad/comments/93o9yi)
  * [Pokemon Go Name Symbols | ୨୧ 𖧧 ﹕ ﹌﹫ ꒰… | Copy & Paste](https://emojicombos.com/pokemon-go-name-symbols)
* Search Strings
  * [Favorite Pokémon Go search strings?](https://www.reddit.com/r/TheSilphRoad/comments/11mvrzo)
  * [Useful Pokemon search strings](https://www.reddit.com/r/TheSilphRoad/comments/netdv7)
  * [In search strings, what "& | , : ;" symbols really mean? And how to use them properly?](https://www.reddit.com/r/TheSilphRoad/comments/17okvx1)
  * [Search strings](https://www.reddit.com/r/TheSilphRoad/comments/1cc86tb)
  * [leidwesen.github.io/SearchPhrases](https://leidwesen.github.io/SearchPhrases/)


## TODO

* [ ] Make everything user editable
  * [ ] Save user settings locally
  * [ ] Allow to share user settings via URL
