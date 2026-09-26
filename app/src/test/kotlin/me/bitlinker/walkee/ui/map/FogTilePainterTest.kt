package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.map.FogCoverage
import me.bitlinker.walkee.data.settings.FogStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

class FogTilePainterTest {

    private val style = FogStyle(opacity = 1f, colorRgb = 0x112233, revealedOpacity = 0.2f, revealedColorRgb = 0xFFD600)
    private val size = FogTilePainter.TILE_SIZE
    private val fog = 0xFF112233.toInt()
    private val tint = (51 shl 24) or 0xFFD600 // 0.2 * 255 = 51

    @Test
    fun `solid tile is uniformly opaque fog`() {
        val pixels = FogTilePainter.solid(style)
        assertEquals(size * size, pixels.size)
        assertTrue(pixels.all { it == fog })

        val half = FogTilePainter.solid(style.copy(opacity = 0.5f))
        assertEquals(0x80, half[0] ushr 24)
    }

    @Test
    fun `revealed tile is uniformly tinted`() {
        assertTrue(FogTilePainter.revealed(style).all { it == tint })
        assertTrue(FogTilePainter.revealed(style.copy(revealedOpacity = 0f)).all { it == 0 })
    }

    @Test
    fun `open cells are tinted and closed cells are fog`() {
        // 2×2 grid: top-left open, bottom-right open, others closed.
        val coverage = FogCoverage(2, intArrayOf(1, 0, 0, 1), capacity = 1)
        val pixels = FogTilePainter.paint(coverage, style)
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
    fun `partial density mixes fog and tint by area`() {
        val coverage = FogCoverage(1, intArrayOf(3), capacity = 4)
        val pixels = FogTilePainter.paint(coverage, style.copy(opacity = 0.8f))
        assertTrue(pixels.all { it == pixels[0] })
        // A quarter is fog at 0.8, three quarters tint at 0.2: alpha 0.2 + 0.15 = 0.35 → 89,
        // colour (fog · 0.2 + tint · 0.15) / 0.35 per channel.
        assertClose(89, pixels[0] ushr 24)
        assertClose(119, (pixels[0] ushr 16) and 0xFF)
        assertClose(111, (pixels[0] ushr 8) and 0xFF)
        assertClose(29, pixels[0] and 0xFF)
    }

    @Test
    fun `without tint partial density only scales fog alpha`() {
        val coverage = FogCoverage(1, intArrayOf(3), capacity = 4)
        val pixels = FogTilePainter.paint(coverage, style.copy(opacity = 0.8f, revealedOpacity = 0f))
        // 0.8 * 255 * (1 - 0.75) = 51
        assertClose(51, pixels[0] ushr 24)
        assertEquals(0x112233, pixels[0] and 0xFFFFFF)
    }

    @Test
    fun `full resolution grid maps one cell per pixel`() {
        val counts = IntArray(size * size)
        counts[size * 10 + 20] = 1
        val pixels = FogTilePainter.paint(FogCoverage(size, counts, capacity = 1), style)
        assertEquals(tint, pixels[size * 10 + 20])
        assertEquals(fog, pixels[size * 10 + 21])
    }

    @Test
    fun `any density mix fits into a palette`() {
        val random = Random(7)
        val capacity = 4096
        val counts = IntArray(size * size) { random.nextInt(0, capacity + 1) }
        val pixels = FogTilePainter.paint(FogCoverage(size, counts, capacity), style)
        assertTrue(pixels.toSet().size <= 256)
        IndexedPngEncoder.encode(pixels, size, size)
    }

    private fun assertClose(expected: Int, actual: Int) =
        assertTrue(abs(expected - actual) <= 1, "expected $expected ± 1, got $actual")
}
