package me.bitlinker.walkee.fog.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.random.Random

class HexLatticeTest {

    /** Moscow's centre in storage cells (see FogGridTest). */
    private val originX = 1_267_712
    private val originY = 657_424

    @ParameterizedTest(name = "display zoom {0}")
    @ValueSource(ints = [16, 17, 18, 19, 20])
    fun `hexAt is the nearest centre, ties included`(displayZoom: Int) {
        val lattice = HexLattice(displayZoom)
        val random = Random(displayZoom)
        // Cell centres are where ties can happen; random points cover everything else.
        for (y in 0 until 40) for (x in 0 until 40) {
            assertEquals(nearestByBruteForce(lattice, originX + x + 0.5, originY + y + 0.5), lattice.hexAt(originX + x + 0.5, originY + y + 0.5))
        }
        repeat(5000) {
            val x = originX + random.nextDouble() * 100
            val y = originY + random.nextDouble() * 100
            assertEquals(nearestByBruteForce(lattice, x, y), lattice.hexAt(x, y), "point ($x, $y)")
        }
    }

    @ParameterizedTest(name = "display zoom {0}")
    @ValueSource(ints = [16, 17, 18, 19, 20])
    fun `every hexagon holds storage cells in proportion to its area`(displayZoom: Int) {
        val lattice = HexLattice(displayZoom)
        val span = (lattice.width * 24).toInt()
        val cellsPerHex = HashMap<HexKey, Int>()
        for (y in 0 until span) for (x in 0 until span) {
            cellsPerHex.merge(lattice.hexAt(originX + x + 0.5, originY + y + 0.5), 1, Int::plus)
        }
        // Hexagons cut by the sampled square are incomplete; keep those well inside it.
        val inner = cellsPerHex.filterKeys { hex ->
            val cx = lattice.centerX(hex.row, hex.col) - originX
            val cy = lattice.centerY(hex.row) - originY
            cx > lattice.width && cx < span - lattice.width && cy > lattice.width && cy < span - lattice.width
        }.values
        val area = lattice.width * lattice.rowSpacing
        assertTrue(inner.size > 50)
        assertTrue(inner.all { it >= 1 }, "empty hexagon at zoom $displayZoom")
        assertTrue(inner.all { abs(it - area) <= area / 4 + 1 }, "cells per hexagon ${inner.min()}..${inner.max()}, area $area")
        assertEquals(area, inner.average(), area * 0.02)
        // Four cells wide: 14 cells per hexagon on average.
        if (displayZoom == 19) assertTrue(inner.all { it in 12..16 })
    }

    @ParameterizedTest(name = "display zoom {0}")
    @ValueSource(ints = [16, 18, 20])
    fun `edge-adjacent storage cells fall into the same or adjacent hexagons`(displayZoom: Int) {
        val lattice = HexLattice(displayZoom)
        for (y in 0 until 60) for (x in 0 until 60) {
            val here = lattice.hexAt(originX + x + 0.5, originY + y + 0.5)
            val adjacent = (0 until HexLattice.DIRECTIONS).map { lattice.neighbour(here, it) } + here
            assertTrue(lattice.hexAt(originX + x + 1.5, originY + y + 0.5) in adjacent)
            assertTrue(lattice.hexAt(originX + x + 0.5, originY + y + 1.5) in adjacent)
        }
    }

    @Test
    fun `grid is seamless across the antimeridian`() {
        val lattice = HexLattice(18)
        val world = (1 shl FogGrid.STORAGE_ZOOM).toDouble()
        assertEquals(1 shl 18, lattice.columns)
        assertEquals(world, lattice.columns * lattice.width)
        val random = Random(3)
        repeat(1000) {
            val x = random.nextDouble() * 20 - 10
            val y = originY + random.nextDouble() * 20
            val west = lattice.hexAt(x, y)
            val east = lattice.hexAt(x + world, y)
            assertEquals(west.row, east.row)
            assertEquals(west.col + lattice.columns, east.col)
        }
    }

    @Test
    fun `neighbours are mutual and one step away`() {
        val lattice = HexLattice(18)
        val opposite = intArrayOf(
            HexLattice.EAST, HexLattice.WEST,
            HexLattice.SOUTH_EAST, HexLattice.SOUTH_WEST,
            HexLattice.NORTH_EAST, HexLattice.NORTH_WEST,
        )
        for (hex in listOf(HexKey.of(4, 7), HexKey.of(5, 7), HexKey.of(-1, -3), HexKey.of(0, 0))) {
            val neighbours = (0 until HexLattice.DIRECTIONS).map { lattice.neighbour(hex, it) }
            assertEquals(6, neighbours.toSet().size)
            for (direction in 0 until HexLattice.DIRECTIONS) {
                val neighbour = neighbours[direction]
                assertEquals(hex, lattice.neighbour(neighbour, opposite[direction]))
                val distance = hypot(
                    lattice.centerX(neighbour.row, neighbour.col) - lattice.centerX(hex.row, hex.col),
                    lattice.centerY(neighbour.row) - lattice.centerY(hex.row),
                )
                assertTrue(distance in lattice.width..lattice.width * 1.01, "$hex → $neighbour: $distance")
            }
        }
    }

    @Test
    fun `distanceToEdge is the distance to the bisector with the neighbour`() {
        val lattice = HexLattice(18)
        val random = Random(9)
        repeat(2000) {
            val x = originX + random.nextDouble() * 50
            val y = originY + random.nextDouble() * 50
            val hex = lattice.hexAt(x, y)
            for (direction in 0 until HexLattice.DIRECTIONS) {
                val neighbour = lattice.neighbour(hex, direction)
                val toHex = hypot(x - lattice.centerX(hex.row, hex.col), y - lattice.centerY(hex.row))
                val toNeighbour = hypot(x - lattice.centerX(neighbour.row, neighbour.col), y - lattice.centerY(neighbour.row))
                val between = hypot(
                    lattice.centerX(neighbour.row, neighbour.col) - lattice.centerX(hex.row, hex.col),
                    lattice.centerY(neighbour.row) - lattice.centerY(hex.row),
                )
                val expected = (toNeighbour * toNeighbour - toHex * toHex) / (2 * between)
                assertEquals(expected, lattice.distanceToEdge(hex, x, y, direction), 1e-6)
                assertTrue(expected >= -1e-9)
            }
        }
    }

    @Test
    fun `row and column ranges cover every point of a rectangle`() {
        val lattice = HexLattice(18)
        val random = Random(5)
        repeat(200) {
            val fromX = originX + random.nextDouble() * 100
            val fromY = originY + random.nextDouble() * 100
            val size = random.nextDouble() * 30
            val rows = lattice.rowRange(fromY, fromY + size)
            val cols = lattice.columnRange(fromX, fromX + size)
            repeat(50) {
                val hex = lattice.hexAt(fromX + random.nextDouble() * size, fromY + random.nextDouble() * size)
                assertTrue(hex.row in rows && hex.col in cols, "$hex outside $rows × $cols")
            }
        }
    }

    @Test
    fun `hexagons one storage cell wide are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { HexLattice(FogGrid.STORAGE_ZOOM) }
        assertThrows(IllegalArgumentException::class.java) { HexLattice(FogGrid.CHUNK_ZOOM - 1) }
        assertEquals(2.0, HexLattice(HexLattice.MAX_DISPLAY_ZOOM).width)
    }

    @ParameterizedTest(name = "display zoom {0}")
    @ValueSource(ints = [16, 17, 18, 19, 20])
    fun `a cell touches exactly the hexagons its points fall into`(displayZoom: Int) {
        val lattice = HexLattice(displayZoom)
        val random = Random(displayZoom)
        val world = 1 shl FogGrid.STORAGE_ZOOM
        // Around Moscow and across the antimeridian, where columns go negative or past the world.
        val cells = List(300) { (originX + random.nextInt(200)) to (originY + random.nextInt(200)) } +
            List(100) { (random.nextInt(-100, 100)) to (originY + random.nextInt(200)) } +
            List(100) { (world + random.nextInt(-100, 100)) to (originY + random.nextInt(200)) }
        for ((x, y) in cells) {
            val touched = touched(lattice, x, y)
            assertTrue(touched.size in 1..2, "cell ($x, $y) touches $touched")
            assertTrue(lattice.hexAt(x + 0.5, y + 0.5) in touched)
            val sampled = sampled(lattice, x, y, 32)
            assertTrue(touched.containsAll(sampled), "cell ($x, $y): touched $touched, sampled $sampled")
            // Slivers can slip between coarse samples; sample those hexagons finely.
            for (hex in touched - sampled) {
                assertTrue(hex in sampled(lattice, x, y, 512), "cell ($x, $y) does not reach $hex")
            }
        }
    }

    @Test
    fun `a boundary line shared with a hexagon does not count as touching`() {
        val lattice = HexLattice(18) // 8 cells wide: edges between hexagons of a row lie on cell boundaries
        val hex = HexKey.of(2, 5)
        val eastEdge = (lattice.centerX(hex.row, hex.col) + lattice.width / 2).toInt()
        val y = lattice.centerY(hex.row).toInt()
        assertEquals(setOf(hex), touched(lattice, eastEdge - 1, y))
        assertEquals(setOf(lattice.neighbour(hex, HexLattice.EAST)), touched(lattice, eastEdge, y))
    }

    @Test
    fun `most cells touch one hexagon, cells on slanted edges two`() {
        val lattice = HexLattice(18)
        val counts = (0 until 14).flatMap { y -> (0 until 8).map { x -> touched(lattice, originX + x, originY + y).size } }
        // One period of the lattice: 8 × 14 cells, 24 of them on slanted edges.
        assertEquals(88, counts.count { it == 1 })
        assertEquals(24, counts.count { it == 2 })
    }

    @Test
    fun `lattices are shared per display zoom`() {
        assertTrue(HexLattice.of(18) === HexLattice.of(18))
        assertEquals(17, HexLattice.of(17).displayZoom)
        assertThrows(IllegalArgumentException::class.java) { HexLattice.of(FogGrid.STORAGE_ZOOM) }
    }

    @Test
    fun `hex keys pack negative rows and columns`() {
        val key = HexKey.of(-3, -70_000)
        assertEquals(-3, key.row)
        assertEquals(-70_000, key.col)
        assertEquals(HexKey.of(-3, -69_999), HexKey.of(key.row, key.col + 1))
    }

    private fun touched(lattice: HexLattice, x: Int, y: Int): Set<HexKey> =
        buildSet { lattice.forEachHexTouching(x, y) { add(it) } }

    /** Hexagons of an `n × n` grid of points inside the cell. */
    private fun sampled(lattice: HexLattice, x: Int, y: Int, n: Int): Set<HexKey> = buildSet {
        for (sy in 0 until n) for (sx in 0 until n) add(lattice.hexAt(x + (sx + 0.5) / n, y + (sy + 0.5) / n))
    }

    /** Nearest centre over a generous neighbourhood; ties go north, then east (see [HexLattice]). */
    private fun nearestByBruteForce(lattice: HexLattice, x: Double, y: Double): HexKey {
        val row0 = floor(y / lattice.rowSpacing).toInt()
        val col0 = floor(x / lattice.width).toInt()
        var best: HexKey? = null
        var bestDistance = Double.MAX_VALUE
        for (row in row0 - 2..row0 + 2) {
            for (col in col0 - 2..col0 + 2) {
                val dx = x - lattice.centerX(row, col)
                val dy = y - lattice.centerY(row)
                val distance = dx * dx + dy * dy
                val current = best
                val better = current == null || distance < bestDistance ||
                    distance == bestDistance && (row < current.row || row == current.row && col > current.col)
                if (better) {
                    best = HexKey.of(row, col)
                    bestDistance = distance
                }
            }
        }
        return best!!
    }
}
