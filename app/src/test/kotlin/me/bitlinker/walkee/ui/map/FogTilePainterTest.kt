package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.map.FogCoverage
import me.bitlinker.walkee.data.settings.FogStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FogTilePainterTest {

    private val style = FogStyle(opacity = 1f, colorRgb = 0x112233)
    private val size = FogTilePainter.TILE_SIZE

    @Test
    fun `solid tile is uniformly opaque fog`() {
        val pixels = FogTilePainter.solid(style)
        assertEquals(size * size, pixels.size)
        assertTrue(pixels.all { it == 0xFF112233.toInt() })

        val half = FogTilePainter.solid(style.copy(opacity = 0.5f))
        assertEquals(0x80, half[0] ushr 24)
    }

    @Test
    fun `open cells are transparent and closed cells are fog`() {
        // 2×2 grid: top-left open, bottom-right open, others closed.
        val coverage = FogCoverage(2, intArrayOf(1, 0, 0, 1), capacity = 1)
        val pixels = FogTilePainter.paint(coverage, style)
        val half = size / 2

        fun at(x: Int, y: Int) = pixels[y * size + x]
        assertEquals(0, at(0, 0))
        assertEquals(0, at(half - 1, half - 1))
        assertEquals(0xFF112233.toInt(), at(half, 0))
        assertEquals(0xFF112233.toInt(), at(0, half))
        assertEquals(0, at(size - 1, size - 1))
        assertEquals(0xFF112233.toInt(), at(size - 1, 0))
    }

    @Test
    fun `partial density scales alpha`() {
        val coverage = FogCoverage(1, intArrayOf(3), capacity = 4)
        val pixels = FogTilePainter.paint(coverage, style.copy(opacity = 0.8f))
        val alpha = pixels[0] ushr 24
        // 0.8 * 255 * (1 - 0.75) = 51
        assertEquals(51, alpha)
        assertTrue(pixels.all { it == pixels[0] })
    }

    @Test
    fun `full resolution grid maps one cell per pixel`() {
        val counts = IntArray(size * size)
        counts[size * 10 + 20] = 1
        val pixels = FogTilePainter.paint(FogCoverage(size, counts, capacity = 1), style)
        assertEquals(0, pixels[size * 10 + 20])
        assertEquals(0xFF112233.toInt(), pixels[size * 10 + 21])
    }
}
