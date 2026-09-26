package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.map.FogCoverage
import me.bitlinker.walkee.data.settings.FogStyle
import kotlin.math.roundToInt

/**
 * Turns a [FogCoverage] grid into ARGB pixels of a 256 px tile. Pure — no Android types — so
 * it is unit-tested on the JVM; PNG encoding happens in [MapFogLayerRenderer].
 *
 * Each grid cell becomes a hard-edged square whose alpha is the fog opacity scaled by the share
 * of still-hidden display cells inside it (ADR 0003).
 */
object FogTilePainter {
    const val TILE_SIZE = 256

    fun paint(coverage: FogCoverage, style: FogStyle): IntArray {
        require(TILE_SIZE % coverage.side == 0) { "Grid side ${coverage.side} does not divide $TILE_SIZE" }
        val pixels = IntArray(TILE_SIZE * TILE_SIZE)
        val block = TILE_SIZE / coverage.side
        val rgb = style.colorRgb and 0xFFFFFF
        val maxAlpha = style.opacity.coerceIn(0f, 1f) * 255f

        for (gy in 0 until coverage.side) {
            for (gx in 0 until coverage.side) {
                val hidden = 1f - coverage.openness(gy * coverage.side + gx)
                val alpha = (maxAlpha * hidden).roundToInt()
                if (alpha == 0) continue
                val color = (alpha shl 24) or rgb
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
    fun solid(style: FogStyle): IntArray {
        val alpha = (style.opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        return IntArray(TILE_SIZE * TILE_SIZE) { (alpha shl 24) or (style.colorRgb and 0xFFFFFF) }
    }
}
