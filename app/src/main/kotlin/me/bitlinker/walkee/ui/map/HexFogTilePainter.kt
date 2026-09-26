package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.map.HexCoverage
import me.bitlinker.walkee.data.settings.FogStyle
import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.HexKey
import me.bitlinker.walkee.fog.geo.HexLattice

/**
 * Turns a [HexCoverage] into ARGB pixels of a 256 px tile: hard-edged pointy-top hexagons (ADR
 * 0003). Pure — no Android types — like [FogTilePainter].
 *
 * A pixel takes the state of the hexagon its centre falls in. Pixels that an edge between an open
 * and a closed hexagon may cross are supersampled [SUBSAMPLES]², and their open share is mixed like
 * a partly revealed square ([FogTilePainter.cellColor]): slanted edges come out antialiased and a
 * tile has at most `SUBSAMPLES² + 1` colours.
 */
object HexFogTilePainter {
    private const val TILE_SIZE = FogTilePainter.TILE_SIZE
    private const val SUBSAMPLES = 4
    private const val SAMPLES = SUBSAMPLES * SUBSAMPLES

    /** Just over `√2 / 2`: every point of a pixel lies within this many pixels of its centre. */
    private const val PIXEL_REACH = 0.7072

    /**
     * How many zooms below the display zoom hexagons are still drawn. A hexagon is
     * `2^(8 + tileZoom − displayZoom)` px wide, so this keeps it at least 4 px; smaller ones are
     * indistinguishable from squares and are drawn as square densities.
     */
    private const val ZOOMS_BELOW_DISPLAY = 6

    fun drawsHexagons(tileZoom: Int, displayZoom: Int): Boolean = tileZoom >= displayZoom - ZOOMS_BELOW_DISPLAY

    fun paint(coverage: HexCoverage, style: FogStyle): IntArray {
        val lattice = coverage.lattice
        val tileSide = Math.scalb(1.0, FogGrid.STORAGE_ZOOM - coverage.tile.zoom)
        val pixelSide = tileSide / TILE_SIZE
        val originX = coverage.tile.x * tileSide
        val originY = coverage.tile.y * tileSide
        val reach = pixelSide * PIXEL_REACH
        val palette = IntArray(SAMPLES + 1) { open -> FogTilePainter.cellColor(1f - open.toFloat() / SAMPLES, style) }
        val edges = edgeMasks(coverage)
        val pixels = IntArray(TILE_SIZE * TILE_SIZE)

        for (py in 0 until TILE_SIZE) {
            val y = originY + (py + 0.5) * pixelSide
            for (px in 0 until TILE_SIZE) {
                val x = originX + (px + 0.5) * pixelSide
                val hex = lattice.hexAt(x, y)
                val mask = if (hex in coverage) edges[(hex.row - coverage.firstRow) * coverage.cols + hex.col - coverage.firstCol] else 0
                val open = if (mask != 0 && nearEdge(lattice, hex, x, y, mask, reach)) {
                    openSamples(coverage, originX, originY, pixelSide, px, py)
                } else if (coverage.isOpen(hex)) {
                    SAMPLES
                } else {
                    0
                }
                pixels[py * TILE_SIZE + px] = palette[open]
            }
        }
        return pixels
    }

    /**
     * Per window hexagon, a bit for every direction whose neighbour is in the other state. Pixels
     * of the tile never reach hexagons outside the window, so those neighbours are skipped.
     */
    private fun edgeMasks(coverage: HexCoverage): IntArray {
        val masks = IntArray(coverage.rows * coverage.cols)
        for (row in 0 until coverage.rows) {
            for (col in 0 until coverage.cols) {
                val hex = HexKey.of(coverage.firstRow + row, coverage.firstCol + col)
                val open = coverage.isOpen(hex)
                var mask = 0
                for (direction in 0 until HexLattice.DIRECTIONS) {
                    val neighbour = coverage.lattice.neighbour(hex, direction)
                    if (neighbour in coverage && coverage.isOpen(neighbour) != open) mask = mask or (1 shl direction)
                }
                masks[row * coverage.cols + col] = mask
            }
        }
        return masks
    }

    /**
     * Whether an edge towards a differing neighbour passes within [reach] of `(x, y)`. A pixel is
     * far smaller than a hexagon, so it can only reach the hexagon's direct neighbours.
     */
    private fun nearEdge(lattice: HexLattice, hex: HexKey, x: Double, y: Double, mask: Int, reach: Double): Boolean {
        for (direction in 0 until HexLattice.DIRECTIONS) {
            if (mask and (1 shl direction) != 0 && lattice.distanceToEdge(hex, x, y, direction) < reach) return true
        }
        return false
    }

    private fun openSamples(coverage: HexCoverage, originX: Double, originY: Double, pixelSide: Double, px: Int, py: Int): Int {
        var open = 0
        for (sy in 0 until SUBSAMPLES) {
            val y = originY + (py + (sy + 0.5) / SUBSAMPLES) * pixelSide
            for (sx in 0 until SUBSAMPLES) {
                val x = originX + (px + (sx + 0.5) / SUBSAMPLES) * pixelSide
                if (coverage.isOpen(coverage.lattice.hexAt(x, y))) open++
            }
        }
        return open
    }
}
