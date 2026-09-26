package me.bitlinker.walkee.fog.geo

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Pointy-top hexagonal display grid anchored to the world (ADR 0001), in *cell units*: projected
 * world coordinates scaled so that one storage cell is `1 × 1`, origin in the north-west corner,
 * y pointing south.
 *
 * - Row `r` is centred at `y = r·S`; its hexagons are [width] (`W`) apart, and odd rows are
 *   shifted east by `W / 2`. `W` equals the side of a square display cell at [displayZoom].
 * - [rowSpacing] `S = 7/8·W`. A regular hexagon would need `√3/2·W`; the 1 % difference is
 *   invisible, and with it every centre is a short binary fraction, so the arithmetic below is
 *   exact for binary-fraction inputs and every tile classifies a point identically.
 * - `W` divides the world width, so columns repeat every [columns] and the grid is seamless across
 *   the antimeridian.
 * - A hexagon is the set of points nearer to its centre than to any other centre. Ties go to the
 *   eastern hexagon within a row and to the northern row between rows.
 * - A storage cell opens every hexagon it overlaps ([forEachHexTouching]).
 *
 * Lattices are immutable; [of] shares one per display zoom so that its touch table is built once.
 */
class HexLattice(val displayZoom: Int) {

    init {
        require(displayZoom in FogGrid.CHUNK_ZOOM..MAX_DISPLAY_ZOOM) {
            "Hexagon display zoom must be in ${FogGrid.CHUNK_ZOOM}..$MAX_DISPLAY_ZOOM, got $displayZoom"
        }
    }

    /** Distance between neighbouring centres in a row, in storage cells. */
    val width: Double = (1 shl (FogGrid.STORAGE_ZOOM - displayZoom)).toDouble()

    /** Distance between rows, in storage cells. */
    val rowSpacing: Double = width * 7 / 8

    /** Columns around the world. */
    val columns: Int = 1 shl displayZoom

    /** Largest distance from a centre to its hexagon's boundary along y (the pointy tip). */
    val halfHeight: Double = (width * width / 4 + rowSpacing * rowSpacing) / (2 * rowSpacing)

    private val diagonal = sqrt(width * width / 4 + rowSpacing * rowSpacing)

    // Per direction: unit vector towards the neighbour's centre and the distance to the shared edge.
    private val unitX = DoubleArray(DIRECTIONS)
    private val unitY = DoubleArray(DIRECTIONS)
    private val edgeDistance = DoubleArray(DIRECTIONS)

    init {
        for (direction in 0 until DIRECTIONS) {
            val dx = when (direction) {
                WEST -> -width
                EAST -> width
                NORTH_WEST, SOUTH_WEST -> -width / 2
                else -> width / 2
            }
            val dy = when (direction) {
                WEST, EAST -> 0.0
                NORTH_WEST, NORTH_EAST -> -rowSpacing
                else -> rowSpacing
            }
            val length = if (dy == 0.0) width else diagonal
            unitX[direction] = dx / length
            unitY[direction] = dy / length
            edgeDistance[direction] = length / 2
        }
    }

    fun centerX(row: Int, col: Int): Double = col * width + rowOffset(row)

    fun centerY(row: Int): Double = row * rowSpacing

    /**
     * The hexagon containing point `(x, y)`. A hexagon reaches less than one [rowSpacing] above and
     * below its centre, so only the two rows around `y` can contain the point, and within a row the
     * nearest centre is the nearest along x.
     */
    fun hexAt(x: Double, y: Double): HexKey {
        // For a binary-fraction y, y / S = y / (7·2^k) is either whole or far from whole compared
        // with the division's rounding error, so the floor is exact.
        val north = floor(y / rowSpacing).toInt()
        val south = north + 1
        val northCol = nearestColumn(north, x)
        val southCol = nearestColumn(south, x)
        val nx = x - centerX(north, northCol)
        val ny = y - centerY(north)
        val sx = x - centerX(south, southCol)
        val sy = y - centerY(south)
        return if (nx * nx + ny * ny <= sx * sx + sy * sy) HexKey.of(north, northCol) else HexKey.of(south, southCol)
    }

    /** Rows that points with `y` in `[fromY, toY)` can fall into. */
    fun rowRange(fromY: Double, toY: Double): IntRange =
        floor(fromY / rowSpacing).toInt()..floor(toY / rowSpacing).toInt() + 1

    /** Columns (in either row parity) that points with `x` in `[fromX, toX)` can fall into. */
    fun columnRange(fromX: Double, toX: Double): IntRange =
        floor(fromX / width).toInt()..floor(toX / width).toInt() + 1

    /** The hexagon sharing an edge with [hex] in [direction] (one of [WEST] … [SOUTH_EAST]). */
    fun neighbour(hex: HexKey, direction: Int): HexKey {
        val row = hex.row
        val col = hex.col
        // Odd rows sit half a width east, so their diagonal neighbours are one column further east.
        val shift = row and 1
        return when (direction) {
            WEST -> HexKey.of(row, col - 1)
            EAST -> HexKey.of(row, col + 1)
            NORTH_WEST -> HexKey.of(row - 1, col - 1 + shift)
            NORTH_EAST -> HexKey.of(row - 1, col + shift)
            SOUTH_WEST -> HexKey.of(row + 1, col - 1 + shift)
            SOUTH_EAST -> HexKey.of(row + 1, col + shift)
            else -> throw IllegalArgumentException("Direction must be 0 until $DIRECTIONS, got $direction")
        }
    }

    /**
     * Signed distance from `(x, y)` to the line of the edge that [hex] shares with its neighbour in
     * [direction]: positive on [hex]'s side. Approximate (square roots), unlike [hexAt].
     */
    fun distanceToEdge(hex: HexKey, x: Double, y: Double, direction: Int): Double {
        val dx = x - centerX(hex.row, hex.col)
        val dy = y - centerY(hex.row)
        return edgeDistance[direction] - (dx * unitX[direction] + dy * unitY[direction])
    }

    /**
     * Calls [action] for every hexagon that storage cell `(cellX, cellY)`, the square
     * `[cellX, cellX + 1] × [cellY, cellY + 1]`, overlaps with positive area: one for most cells,
     * more along edges and at vertices. A cell that only shares a boundary line with a hexagon
     * does not count. Columns are not wrapped, like [hexAt].
     */
    inline fun forEachHexTouching(cellX: Int, cellY: Int, action: (HexKey) -> Unit) {
        val table = touchTable
        val periodX = table.periodX
        val periodY = table.periodY
        val shiftX = Math.floorDiv(cellX, periodX)
        val shiftY = Math.floorDiv(cellY, periodY)
        val entry = (cellY - shiftY * periodY) * periodX + (cellX - shiftX * periodX)
        val rowShift = shiftY * table.periodRows
        for (i in table.start[entry] until table.start[entry + 1]) {
            action(HexKey.of(table.rows[i] + rowShift, table.cols[i] + shiftX))
        }
    }

    /**
     * Hexagons touched by each cell of one period of the lattice. Shifting by [periodX] cells east
     * moves every hexagon one column; shifting by [periodY] cells south moves it [periodRows] rows,
     * an even number, so row parity and with it the column offset are preserved.
     */
    @PublishedApi
    internal class TouchTable(
        val periodX: Int,
        val periodY: Int,
        val periodRows: Int,
        /** Entry `y · periodX + x` spans `start[entry] until start[entry + 1]` of [rows] and [cols]. */
        val start: IntArray,
        val rows: IntArray,
        val cols: IntArray,
    )

    @PublishedApi
    internal val touchTable: TouchTable by lazy(::buildTouchTable)

    private fun buildTouchTable(): TouchTable {
        val periodX = width.toInt()
        var periodRows = 2
        while ((periodRows * rowSpacing) % 1.0 != 0.0) periodRows *= 2
        val periodY = (periodRows * rowSpacing).toInt()
        val start = IntArray(periodX * periodY + 1)
        val rows = ArrayList<Int>()
        val cols = ArrayList<Int>()
        for (y in 0 until periodY) {
            for (x in 0 until periodX) {
                start[y * periodX + x] = rows.size
                for (row in rowRange(y.toDouble(), y + 1.0)) {
                    for (col in columnRange(x.toDouble(), x + 1.0)) {
                        if (overlapArea(HexKey.of(row, col), x, y) > MIN_OVERLAP) {
                            rows += row
                            cols += col
                        }
                    }
                }
            }
        }
        start[periodX * periodY] = rows.size
        return TouchTable(periodX, periodY, periodRows, start, rows.toIntArray(), cols.toIntArray())
    }

    /** Area of the unit square at `(x, y)` inside [hex]: the square clipped by the six edge lines. */
    private fun overlapArea(hex: HexKey, x: Int, y: Int): Double {
        var xs = listOf(x.toDouble(), x + 1.0, x + 1.0, x.toDouble())
        var ys = listOf(y.toDouble(), y.toDouble(), y + 1.0, y + 1.0)
        for (direction in 0 until DIRECTIONS) {
            val clippedX = ArrayList<Double>(xs.size + 1)
            val clippedY = ArrayList<Double>(xs.size + 1)
            for (i in xs.indices) {
                val j = (i + 1) % xs.size
                val di = distanceToEdge(hex, xs[i], ys[i], direction)
                val dj = distanceToEdge(hex, xs[j], ys[j], direction)
                if (di >= 0) {
                    clippedX += xs[i]
                    clippedY += ys[i]
                }
                if ((di >= 0) != (dj >= 0)) {
                    val t = di / (di - dj)
                    clippedX += xs[i] + t * (xs[j] - xs[i])
                    clippedY += ys[i] + t * (ys[j] - ys[i])
                }
            }
            if (clippedX.size < 3) return 0.0
            xs = clippedX
            ys = clippedY
        }
        var twiceArea = 0.0
        for (i in xs.indices) {
            val j = (i + 1) % xs.size
            twiceArea += xs[i] * ys[j] - xs[j] * ys[i]
        }
        return abs(twiceArea) / 2
    }

    private fun rowOffset(row: Int): Double = if (row and 1 == 0) 0.0 else width / 2

    private fun nearestColumn(row: Int, x: Double): Int = floor((x - rowOffset(row)) / width + 0.5).toInt()

    companion object {
        /**
         * Hexagons must be at least two storage cells wide: one cell wide, they would be no larger
         * than the cells that open them, and every cell would open several at once.
         */
        const val MAX_DISPLAY_ZOOM = FogGrid.STORAGE_ZOOM - 1

        /**
         * Overlaps below this are rounding noise from cells that only share a boundary line with a
         * hexagon: vertices lie on multiples of `W / 112`, so real overlaps are orders larger.
         */
        private const val MIN_OVERLAP = 1e-9

        private val shared = arrayOfNulls<HexLattice>(MAX_DISPLAY_ZOOM + 1)

        /** The shared lattice for [displayZoom]. */
        fun of(displayZoom: Int): HexLattice =
            shared.getOrNull(displayZoom) ?: HexLattice(displayZoom).also { shared[displayZoom] = it }

        const val WEST = 0
        const val EAST = 1
        const val NORTH_WEST = 2
        const val NORTH_EAST = 3
        const val SOUTH_WEST = 4
        const val SOUTH_EAST = 5
        const val DIRECTIONS = 6
    }
}
