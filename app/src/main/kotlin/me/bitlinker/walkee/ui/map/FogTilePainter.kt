package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.map.FogCoverage
import me.bitlinker.walkee.data.settings.FogStyle
import kotlin.math.roundToInt

/**
 * Turns a [FogCoverage] grid into ARGB pixels of a 256 px tile. Pure — no Android types — so
 * it is unit-tested on the JVM; PNG encoding happens in [MapFogLayerRenderer].
 *
 * Each grid cell becomes a hard-edged square. Hidden cells are fog, revealed cells carry the
 * revealed tint, and partly revealed cells (zoomed out) mix the two by area (ADR 0003).
 */
object FogTilePainter {
    const val TILE_SIZE = 256

    /**
     * Hidden shares are quantised to this many steps. Colours depend only on the step, so a tile
     * never has more than 256 of them and always fits a palette PNG.
     */
    private const val LEVELS = 255

    fun paint(coverage: FogCoverage, style: FogStyle): IntArray {
        require(TILE_SIZE % coverage.side == 0) { "Grid side ${coverage.side} does not divide $TILE_SIZE" }
        val pixels = IntArray(TILE_SIZE * TILE_SIZE)
        val block = TILE_SIZE / coverage.side
        val palette = IntArray(LEVELS + 1) { level -> cellColor(level.toFloat() / LEVELS, style) }

        for (gy in 0 until coverage.side) {
            for (gx in 0 until coverage.side) {
                val hidden = 1f - coverage.openness(gy * coverage.side + gx)
                val color = palette[(hidden * LEVELS).roundToInt()]
                if (color == 0) continue
                val x0 = gx * block
                val y0 = gy * block
                for (y in y0 until y0 + block) {
                    pixels.fill(color, y * TILE_SIZE + x0, y * TILE_SIZE + x0 + block)
                }
            }
        }
        return pixels
    }

    /** Uniformly hidden tile. */
    fun solid(style: FogStyle): IntArray = IntArray(TILE_SIZE * TILE_SIZE) { cellColor(1f, style) }

    /** Uniformly revealed tile. */
    fun revealed(style: FogStyle): IntArray = IntArray(TILE_SIZE * TILE_SIZE) { cellColor(0f, style) }

    /**
     * Colour of a cell whose share [hidden] of display cells is fog and the rest revealed: the
     * area average of "fog over the map" and "tint over the map", expressed as one ARGB colour.
     */
    fun cellColor(hidden: Float, style: FogStyle): Int {
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
