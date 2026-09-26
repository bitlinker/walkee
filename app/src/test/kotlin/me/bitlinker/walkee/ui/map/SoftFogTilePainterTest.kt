package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.map.FogCoverage
import me.bitlinker.walkee.data.settings.FogStyle
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.random.Random

class SoftFogTilePainterTest {

    private val size = FogTilePainter.TILE_SIZE
    private val soft = SoftFogTilePainter(noise = false)
    private val clouds = SoftFogTilePainter(noise = true)

    /** Pure black fog without tint: a pixel's alpha is its hidden level, `0..255`. */
    private val levels = FogPalette(FogStyle(opacity = 1f, colorRgb = 0, revealedOpacity = 0f))

    /** A patch of the world in display cells: scattered cells, a one-cell path, a block and a lone cell. */
    private val worldSide = 24
    private val world = IntArray(worldSide * worldSide).also { cells ->
        val random = Random(3)
        for (y in 2..10) for (x in 2..21) if (random.nextInt(4) == 0) cells[y * worldSide + x] = 1
        for (x in 3..20) cells[16 * worldSide + x] = 1
        for (y in 19..21) for (x in 4..6) cells[y * worldSide + x] = 1
        cells[20 * worldSide + 18] = 1
    }

    @Test
    fun `margin covers the blur until cells get smaller than two pixels`() {
        assertEquals(2, soft.margin(tileZoom = 20, displayZoom = 18))
        assertEquals(2, soft.margin(tileZoom = 18, displayZoom = 18))
        assertEquals(2, soft.margin(tileZoom = 11, displayZoom = 18)) // 2 px per cell
        assertEquals(0, soft.margin(tileZoom = 10, displayZoom = 18)) // 1 px per cell
        assertEquals(0, soft.margin(tileZoom = 3, displayZoom = 18))
    }

    @Test
    fun `tiles match the exact Gaussian field of the whole world`() {
        // Neighbouring tiles, different cell sizes and tiles smaller than a cell: each must agree with
        // one field evaluated over the whole world, which also means tiles meet without seams.
        val tiles = listOf(
            TileSpec(cellX = 4, cellY = 4, side = 8),
            TileSpec(cellX = 12, cellY = 4, side = 8),
            TileSpec(cellX = 4, cellY = 12, side = 8),
            TileSpec(cellX = 12, cellY = 12, side = 8),
            TileSpec(cellX = 8, cellY = 14, side = 4),
            TileSpec(cellX = 17, cellY = 19, side = 1),
            TileSpec(cellX = 6, cellY = 16, side = 1, subdivision = 2, subX = 3, subY = 1),
        )
        for (spec in tiles) {
            val pixels = soft.paint(coverageOf(spec), levels)
            val expected = referenceLevels(spec)
            for (i in pixels.indices) {
                val actual = pixels[i] ushr 24
                assertTrue(abs(actual - expected[i]) <= 1) {
                    "$spec pixel (${i % size}, ${i / size}): expected ${expected[i]} ± 1, got $actual"
                }
            }
        }
    }

    @Test
    fun `overlapping tiles agree exactly, noise included`() {
        // Two windows half a tile apart: their shared half shows the same world, so pixels must match.
        for (painter in listOf(soft, clouds)) {
            val left = painter.paint(coverageOf(TileSpec(cellX = 4, cellY = 8, side = 8), worldOffset = 150_000), levels)
            val right = painter.paint(coverageOf(TileSpec(cellX = 8, cellY = 8, side = 8), worldOffset = 150_000), levels)
            for (y in 0 until size) {
                for (x in 0 until size / 2) {
                    assertEquals(left[y * size + x + size / 2], right[y * size + x], "pixel ($x, $y)")
                }
            }
        }
    }

    @Test
    fun `uniform neighbourhoods stay exactly uniform`() {
        val open = FogCoverage(8, IntArray(12 * 12) { 1 }, capacity = 1, margin = 2)
        val closed = FogCoverage(8, IntArray(12 * 12), capacity = 1, margin = 2)
        for (painter in listOf(soft, clouds)) {
            assertTrue(painter.paint(open, levels).all { it == levels.revealedColor })
            assertTrue(painter.paint(closed, levels).all { it == levels.hiddenColor })
        }
    }

    @Test
    fun `a lone open cell stays visible and rounds off`() {
        val counts = IntArray(5 * 5).also { it[2 * 5 + 2] = 1 }
        val pixels = soft.paint(FogCoverage(1, counts, capacity = 1, margin = 2), levels)
        fun level(x: Int, y: Int) = pixels[y * size + x] ushr 24
        assertTrue(level(size / 2, size / 2) < 48, "centre is mostly revealed: ${level(size / 2, size / 2)}")
        assertTrue(level(0, 0) > 220, "corner is fog: ${level(0, 0)}")
        // Rounded: the middle of an edge is clearer than the corner.
        assertTrue(level(size / 2, 0) < level(0, 0))
    }

    @Test
    fun `a one-cell path is fully revealed along its middle`() {
        val counts = IntArray(5 * 5).also { for (x in 0 until 5) it[2 * 5 + x] = 1 }
        val pixels = soft.paint(FogCoverage(1, counts, capacity = 1, margin = 2), levels)
        for (x in 0 until size) assertEquals(0, pixels[(size / 2) * size + x] ushr 24)
    }

    @Test
    fun `clouds only touch the edges`() {
        // Open on the left six columns of the padded grid (the tile's cells 0..3), fog on the right.
        val counts = IntArray(12 * 12) { if (it % 12 < 6) 1 else 0 }
        val coverage = FogCoverage(8, counts, capacity = 1, margin = 2, originX = 7_000, originY = 9_000)
        val cloudy = clouds.paint(coverage, levels)
        val smooth = soft.paint(coverage, levels)
        for (y in 0 until size) {
            // Two or more cells from the edge (x = 128 px) nothing changes.
            for (x in 0 until 64) assertEquals(levels.revealedColor, cloudy[y * size + x])
            for (x in 192 until size) assertEquals(levels.hiddenColor, cloudy[y * size + x])
        }
        assertTrue(cloudy.indices.any { cloudy[it] != smooth[it] }, "noise changes the edge")
    }

    @Test
    fun `tiny cells fall back to squares`() {
        val random = Random(1)
        val coverage = FogCoverage(size, IntArray(size * size) { random.nextInt(2) }, capacity = 1)
        assertArrayEquals(SquareFogTilePainter.paint(coverage, levels), soft.paint(coverage, levels))
    }

    private data class TileSpec(
        val cellX: Int,
        val cellY: Int,
        val side: Int,
        val subdivision: Int = 0,
        val subX: Int = 0,
        val subY: Int = 0,
    ) {
        val cellPx: Int get() = if (subdivision > 0) 256 shl subdivision else 256 / side
    }

    /** Coverage of a tile whose first own cell is world cell ([TileSpec.cellX], [TileSpec.cellY]). */
    private fun coverageOf(spec: TileSpec, margin: Int = 2, worldOffset: Int = 0): FogCoverage {
        val stride = spec.side + 2 * margin
        val counts = IntArray(stride * stride) { i ->
            val x = spec.cellX - margin + i % stride
            val y = spec.cellY - margin + i / stride
            if (x in 0 until worldSide && y in 0 until worldSide) world[y * worldSide + x] else 0
        }
        return FogCoverage(
            spec.side, counts, capacity = 1, margin = margin,
            subdivision = spec.subdivision, subX = spec.subX, subY = spec.subY,
            originX = worldOffset + spec.cellX - margin, originY = worldOffset + spec.cellY - margin,
        )
    }

    /** Hidden levels from the untruncated, unnormalised field over every world cell. */
    private fun referenceLevels(spec: TileSpec): IntArray {
        fun axis(cell: Int, sub: Int): Array<DoubleArray> = Array(size) { p ->
            val u = cell + (sub * size + p + 0.5) / spec.cellPx
            DoubleArray(worldSide) { c -> phi((u - c) / SoftFogTilePainter.SIGMA) - phi((u - c - 1) / SoftFogTilePainter.SIGMA) }
        }
        val wx = axis(spec.cellX, spec.subX)
        val wy = axis(spec.cellY, spec.subY)
        val rows = Array(worldSide) { cy ->
            DoubleArray(size) { px -> (0 until worldSide).sumOf { cx -> world[cy * worldSide + cx] * wx[px][cx] } }
        }
        return IntArray(size * size) { i ->
            val px = i % size
            val py = i / size
            val field = (0 until worldSide).sumOf { cy -> wy[py][cy] * rows[cy][px] }
            val t = ((field - SoftFogTilePainter.EDGE_LOW) / (SoftFogTilePainter.EDGE_HIGH - SoftFogTilePainter.EDGE_LOW)).coerceIn(0.0, 1.0)
            ((1 - t * t * (3 - 2 * t)) * 255).roundToInt()
        }
    }

    private fun phi(x: Double): Double = 0.5 * (1 + erf(x / sqrt(2.0)))

    /**
     * Maclaurin series — independent of the painter's approximation. Beyond 3.5 the tail
     * (`erfc(3.5) < 1e-6`) is negligible and the series would start losing precision.
     */
    private fun erf(x: Double): Double {
        if (abs(x) > 3.5) return sign(x)
        var power = x
        var sum = x
        for (n in 1..100) {
            power *= -x * x / n
            sum += power / (2 * n + 1)
        }
        return 2 / sqrt(PI) * sum
    }
}
