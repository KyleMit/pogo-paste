package app.pogopaste.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/** Colors for one theme. Resolved once per keyboard build from the system night mode. */
class Palette(
    val background: Int,
    val key: Int,
    val keyPressed: Int,
    val keySpecial: Int,
    val text: Int,
    val subtext: Int,
    val accent: Int,
    val onAccent: Int,
    val divider: Int,
) {
    companion object {
        fun isNight(context: Context) =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        fun of(context: Context): Palette = if (isNight(context)) DARK else LIGHT

        val LIGHT = Palette(
            background = Color.rgb(0xE8, 0xEA, 0xED),
            key = Color.WHITE,
            keyPressed = Color.rgb(0xC9, 0xCD, 0xD2),
            keySpecial = Color.rgb(0xD3, 0xD7, 0xDC),
            text = Color.rgb(0x1B, 0x1B, 0x1F),
            subtext = Color.rgb(0x5F, 0x63, 0x68),
            accent = Color.rgb(0x3F, 0x7D, 0xBA),
            onAccent = Color.WHITE,
            divider = Color.rgb(0xC4, 0xC7, 0xCC),
        )

        val DARK = Palette(
            background = Color.rgb(0x1B, 0x1C, 0x1F),
            key = Color.rgb(0x2E, 0x30, 0x35),
            keyPressed = Color.rgb(0x4A, 0x4D, 0x54),
            keySpecial = Color.rgb(0x24, 0x26, 0x2A),
            text = Color.rgb(0xE8, 0xEA, 0xED),
            subtext = Color.rgb(0x9A, 0xA0, 0xA6),
            accent = Color.rgb(0x6C, 0xA6, 0xE0),
            onAccent = Color.rgb(0x0B, 0x1E, 0x33),
            divider = Color.rgb(0x3A, 0x3C, 0x41),
        )
    }
}
