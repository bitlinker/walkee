package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.map.FogCoverage
import me.bitlinker.walkee.ui.map.FogTilePainter.Companion.TILE_SIZE
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Soft fog (ADR 0003): the grid is treated as an image of open shares, one texel per display cell,
 * blurred with a Gaussian — every cell is a unit square convolved with `N(0, σ²)` — and turned into
 * fog through a smoothstep. Cells keep their place and size, corners round off, diagonal
 * staircases melt into slopes, and the edge becomes a translucent gradient, as in RTS fog of war.
 *
 * The blur is separable and evaluated exactly: along one axis a cell spanning `[c, c + 1]` weighs
 * `Φ((u − c) / σ) − Φ((u − c − 1) / σ)` at position `u`. It reaches [MARGIN] cells beyond the tile,
 * which the coverage supplies, so neighbouring tiles agree on their shared edge. Everything is
 * measured in display cells, so shapes do not change between zoom levels.
 *
 * With [noise], value noise shifts the field near edges for a cloudy outline. It depends only on
 * world coordinates, so it is seamless across tiles and stable across redraws.
 */
class SoftFogTilePainter(private val noise: Boolean) : FogTilePainter {

    override fun margin(tileZoom: Int, displayZoom: Int): Int {
        val zoomOut = displayZoom - tileZoom
        return if (zoomOut <= 0 || TILE_SIZE shr zoomOut >= MIN_CELL_PX) MARGIN else 0
    }

    override fun paint(coverage: FogCoverage, palette: FogPalette): IntArray {
        val cellPx = if (coverage.subdivision > 0) TILE_SIZE shl coverage.subdivision else TILE_SIZE / coverage.side
        // Below two pixels per cell a blur is invisible and squares are the same picture.
        if (coverage.margin < MARGIN || cellPx < MIN_CELL_PX) return SquareFogTilePainter.paint(coverage, palette)

        val stride = coverage.stride
        val open = FloatArray(stride * stride) { coverage.openCounts[it].toFloat() / coverage.capacity }
        val xAxis = AxisWeights(coverage.subX * TILE_SIZE, cellPx, coverage.margin)
        val yAxis = AxisWeights(coverage.subY * TILE_SIZE, cellPx, coverage.margin)

        // Horizontal pass: every padded row blurred along x, sampled at the tile's pixel columns.
        val rows = FloatArray(stride * TILE_SIZE)
        for (row in 0 until stride) {
            val cells = row * stride
            if (isZero(open, cells, stride)) continue
            val out = row * TILE_SIZE
            for (px in 0 until TILE_SIZE) {
                val first = cells + xAxis.first[px]
                val w = px * TAPS
                var sum = 0f
                for (tap in 0 until TAPS) sum += xAxis.weights[w + tap] * open[first + tap]
                rows[out + px] = sum
            }
        }

        // Vertical pass, then the field (open share) becomes a palette colour.
        val pixels = IntArray(TILE_SIZE * TILE_SIZE)
        val field = FloatArray(TILE_SIZE)
        for (py in 0 until TILE_SIZE) {
            field.fill(0f)
            for (tap in 0 until TAPS) {
                val weight = yAxis.weights[py * TAPS + tap]
                val base = (yAxis.first[py] + tap) * TILE_SIZE
                for (px in 0 until TILE_SIZE) field[px] += weight * rows[base + px]
            }
            val worldY = coverage.originY + yAxis.position[py]
            val out = py * TILE_SIZE
            for (px in 0 until TILE_SIZE) {
                var f = field[px]
                if (noise && f > NOISE_FROM && f < EDGE_HIGH + NOISE_AMPLITUDE) {
                    val worldX = coverage.originX + xAxis.position[px]
                    f += NOISE_AMPLITUDE * smoothstep(NOISE_FROM, NOISE_FULL, f) * cloudNoise(worldX, worldY)
                }
                pixels[out + px] = palette.color(1f - smoothstep(EDGE_LOW, EDGE_HIGH, f))
            }
        }
        return pixels
    }

    /**
     * Blur weights of one axis: pixel `p` covers cells `first[p] until first[p] + TAPS` of the
     * padded grid with weights normalised to 1, so uniform areas stay exactly uniform.
     */
    private class AxisWeights(offsetPx: Int, cellPx: Int, margin: Int) {
        /** Pixel centres in padded-grid cells. */
        val position = DoubleArray(TILE_SIZE)
        val first = IntArray(TILE_SIZE)
        val weights = FloatArray(TILE_SIZE * TAPS)

        init {
            val raw = DoubleArray(TAPS)
            for (p in 0 until TILE_SIZE) {
                val u = margin + (offsetPx + p + 0.5) / cellPx
                val firstCell = floor(u).toInt() - REACH
                position[p] = u
                first[p] = firstCell
                var total = 0.0
                for (tap in 0 until TAPS) {
                    val d = u - (firstCell + tap)
                    raw[tap] = phi(d / SIGMA) - phi((d - 1.0) / SIGMA)
                    total += raw[tap]
                }
                for (tap in 0 until TAPS) weights[p * TAPS + tap] = (raw[tap] / total).toFloat()
            }
        }
    }

    companion object {
        /** Blur radius in display cells: rounds corners and diagonals, keeps one-cell paths. */
        internal const val SIGMA = 0.45

        /**
         * Open-share range of the edge gradient. A straight edge of an open area sits at 0.5, a
         * lone open cell peaks at ≈ 0.54 and a one-cell path at ≈ 0.73, so both stay visible.
         */
        internal const val EDGE_LOW = 0.15f
        internal const val EDGE_HIGH = 0.65f

        /** Cells beyond the pixel's own reached by the blur: the weight past them is `< 1 − Φ(4)`. */
        private const val REACH = 2
        private const val TAPS = 2 * REACH + 1
        private const val MARGIN = REACH

        /** Smaller cells are painted as squares. */
        private const val MIN_CELL_PX = 2

        /** Noise shifts the field by up to this much; it fades in over `NOISE_FROM..NOISE_FULL`, keeping deep fog intact. */
        private const val NOISE_AMPLITUDE = 0.14f
        private const val NOISE_FROM = 0.02f
        private const val NOISE_FULL = 0.3f

        /** Two octaves of value noise, frequencies in lattice points per display cell. */
        private const val OCTAVE_1_FREQUENCY = 1.3
        private const val OCTAVE_1_WEIGHT = 0.65f
        private const val OCTAVE_2_FREQUENCY = 2.9
        private const val OCTAVE_2_WEIGHT = 0.35f

        private val SQRT_2 = sqrt(2.0)

        private fun isZero(values: FloatArray, from: Int, length: Int): Boolean {
            for (i in from until from + length) if (values[i] != 0f) return false
            return true
        }

        private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /** Standard normal CDF. */
        private fun phi(x: Double): Double = 0.5 * (1.0 + erf(x / SQRT_2))

        /** Abramowitz–Stegun 7.1.26, absolute error below 1.5e-7. */
        private fun erf(x: Double): Double {
            val t = 1.0 / (1.0 + 0.3275911 * abs(x))
            val poly = ((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t
            val y = 1.0 - poly * exp(-x * x)
            return if (x >= 0) y else -y
        }

        /** Fractal value noise in `−1..1` at a world position measured in display cells. */
        private fun cloudNoise(x: Double, y: Double): Float =
            OCTAVE_1_WEIGHT * valueNoise(x * OCTAVE_1_FREQUENCY, y * OCTAVE_1_FREQUENCY, seed = 1L) +
                OCTAVE_2_WEIGHT * valueNoise(x * OCTAVE_2_FREQUENCY, y * OCTAVE_2_FREQUENCY, seed = 2L)

        private fun valueNoise(x: Double, y: Double, seed: Long): Float {
            val x0 = floor(x)
            val y0 = floor(y)
            val sx = fade((x - x0).toFloat())
            val sy = fade((y - y0).toFloat())
            val ix = x0.toLong()
            val iy = y0.toLong()
            val top = lerp(lattice(ix, iy, seed), lattice(ix + 1, iy, seed), sx)
            val bottom = lerp(lattice(ix, iy + 1, seed), lattice(ix + 1, iy + 1, seed), sx)
            return lerp(top, bottom, sy)
        }

        private fun fade(t: Float): Float = t * t * (3f - 2f * t)

        private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        /** Pseudo-random value in `−1..1` for a lattice point (SplitMix64 finaliser). */
        private fun lattice(x: Long, y: Long, seed: Long): Float {
            var h = x * -0x61c8864680b583ebL + y * 0x5851f42d4c957f2dL + seed // 0x9e3779b97f4a7c15
            h = (h xor (h ushr 30)) * -0x40a7b892e31b1a47L // 0xbf58476d1ce4e5b9
            h = (h xor (h ushr 27)) * -0x6b2fb644ecceee15L // 0x94d049bb133111eb
            h = h xor (h ushr 31)
            return (h ushr 40).toFloat() / (1 shl 24) * 2f - 1f
        }
    }
}
