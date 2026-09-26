package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.map.FogCoverage
import me.bitlinker.walkee.ui.map.FogTilePainter.Companion.TILE_SIZE

/**
 * Hard-edged squares: each of the tile's grid cells is filled with the colour of its hidden share.
 * Hidden cells are fog, revealed cells carry the tint, and partly revealed cells (zoomed out) mix
 * the two by area (ADR 0003).
 */
object SquareFogTilePainter : FogTilePainter {

    override fun margin(tileZoom: Int, displayZoom: Int): Int = 0

    override fun paint(coverage: FogCoverage, palette: FogPalette): IntArray {
        val side = coverage.side
        require(TILE_SIZE % side == 0) { "Grid side $side does not divide $TILE_SIZE" }
        val pixels = IntArray(TILE_SIZE * TILE_SIZE)
        val block = TILE_SIZE / side

        for (gy in 0 until side) {
            for (gx in 0 until side) {
                val color = palette.color(1f - coverage.openness(gx, gy))
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
}
