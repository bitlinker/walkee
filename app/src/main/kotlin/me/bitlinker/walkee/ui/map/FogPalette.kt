package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.settings.FogStyle
import kotlin.math.roundToInt

/**
 * Colours of fog tiles by hidden share, shared by every [FogTilePainter] (ADR 0003): level `0` is
 * fully revealed (the tint alone), level [LEVELS] fully hidden (the fog alone), and levels in
 * between mix the two by area. Colours depend only on the level, so a tile never has more than
 * 256 of them and always fits a palette PNG.
 */
class FogPalette(style: FogStyle) {
    private val colors = IntArray(LEVELS + 1) { level -> colorOf(level.toFloat() / LEVELS, style) }

    val hiddenColor: Int get() = colors[LEVELS]

    val revealedColor: Int get() = colors[0]

    /** Colour for a share [hidden] of fog, `0f..1f` (clamped), quantised to the nearest level. */
    fun color(hidden: Float): Int = colors[(hidden.coerceIn(0f, 1f) * LEVELS + 0.5f).toInt()]

    companion object {
        /** Hidden shares are quantised to this many steps. */
        const val LEVELS = 255

        /**
         * Colour of an area whose share [hidden] is fog and the rest revealed: the area average of
         * "fog over the map" and "tint over the map", expressed as one ARGB colour.
         */
        fun colorOf(hidden: Float, style: FogStyle): Int {
            val fogAlpha = style.opacity.coerceIn(0f, 1f) * hidden
            val tintAlpha = style.revealedOpacity.coerceIn(0f, 1f) * (1f - hidden)
            val alpha = fogAlpha + tintAlpha
            val alpha8 = (alpha * 255f).roundToInt()
            if (alpha8 == 0) return 0

            fun channel(shift: Int): Int {
                val fog = (style.colorRgb ushr shift) and 0xFF
                val tint = (style.revealedColorRgb ushr shift) and 0xFF
                return ((fog * fogAlpha + tint * tintAlpha) / alpha).roundToInt()
            }
            return (alpha8 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }
}
