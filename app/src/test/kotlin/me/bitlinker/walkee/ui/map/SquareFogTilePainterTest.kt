package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.map.FogCoverage
import me.bitlinker.walkee.data.settings.FogStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SquareFogTilePainterTest {

    private val palette = FogPalette(FogStyle(opacity = 1f, colorRgb = 0x112233, revealedOpacity = 0.2f, revealedColorRgb = 0xFFD600))
    private val size = FogTilePainter.TILE_SIZE
    private val fog = palette.hiddenColor
    private val tint = palette.revealedColor

    @Test
    fun `needs no margin`() {
        assertEquals(0, SquareFogTilePainter.margin(tileZoom = 18, displayZoom = 18))
        assertEquals(0, SquareFogTilePainter.margin(tileZoom = 14, displayZoom = 18))
    }

    @Test
    fun `open cells are tinted and closed cells are fog`() {
        // 2×2 grid: top-left open, bottom-right open, others closed.
        val coverage = FogCoverage(2, intArrayOf(1, 0, 0, 1), capacity = 1)
        val pixels = SquareFogTilePainter.paint(coverage, palette)
        val half = size / 2

        fun at(x: Int, y: Int) = pixels[y * size + x]
        assertEquals(tint, at(0, 0))
        assertEquals(tint, at(half - 1, half - 1))
        assertEquals(fog, at(half, 0))
        assertEquals(fog, at(0, half))
        assertEquals(tint, at(size - 1, size - 1))
        assertEquals(fog, at(size - 1, 0))
    }

    @Test
    fun `margin cells are ignored`() {
        // 1×1 tile with a margin of 1: only the centre of the 3×3 grid is the tile's own cell.
        val closedInOpen = FogCoverage(1, IntArray(9) { if (it == 4) 0 else 1 }, capacity = 1, margin = 1)
        assertTrue(SquareFogTilePainter.paint(closedInOpen, palette).all { it == fog })
    }

    @Test
    fun `a tile inside one cell takes that cell's colour`() {
        val coverage = FogCoverage(1, intArrayOf(1), capacity = 1, subdivision = 2, subX = 3, subY = 1)
        assertTrue(SquareFogTilePainter.paint(coverage, palette).all { it == tint })
    }

    @Test
    fun `partial density fills the whole cell with the mixed colour`() {
        val pixels = SquareFogTilePainter.paint(FogCoverage(1, intArrayOf(3), capacity = 4), palette)
        assertTrue(pixels.all { it == palette.color(0.25f) })
    }

    @Test
    fun `full resolution grid maps one cell per pixel`() {
        val counts = IntArray(size * size)
        counts[size * 10 + 20] = 1
        val pixels = SquareFogTilePainter.paint(FogCoverage(size, counts, capacity = 1), palette)
        assertEquals(tint, pixels[size * 10 + 20])
        assertEquals(fog, pixels[size * 10 + 21])
    }

    @Test
    fun `any density mix fits into a palette`() {
        val random = Random(7)
        val capacity = 4096
        val counts = IntArray(size * size) { random.nextInt(0, capacity + 1) }
        val pixels = SquareFogTilePainter.paint(FogCoverage(size, counts, capacity), palette)
        assertTrue(pixels.toSet().size <= 256)
        IndexedPngEncoder.encode(pixels, size, size)
    }
}
