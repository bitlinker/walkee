package me.bitlinker.walkee.data.map

import me.bitlinker.walkee.fog.geo.Epsg3395
import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.GeoPoint
import me.bitlinker.walkee.fog.geo.TileKey
import me.bitlinker.walkee.fog.geo.WorldPoint
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs

class FogBrushTest {

    private val moscow = GeoPoint(55.7558, 37.6173)

    @Test
    fun `zero radius reveals exactly the centre cell`() {
        assertEquals(listOf(FogGrid.cellAt(moscow)), FogBrush.diskCells(moscow, 0.0))
        assertEquals(listOf(FogGrid.cellAt(moscow)), FogBrush.diskCells(moscow, 1.0))
    }

    @Test
    fun `disk area matches the expected number of cells`() {
        val radius = 30.0
        val cells = FogBrush.diskCells(moscow, radius)
        val cellSide = FogGrid.cellSizeMetres(moscow.latitude)
        val expected = PI * radius * radius / (cellSide * cellSide)
        assertTrue(cells.contains(FogGrid.cellAt(moscow)))
        assertTrue(abs(cells.size - expected) <= expected * 0.5 + 2, "got ${cells.size} cells, expected about $expected")
        assertEquals(cells.size, cells.toSet().size, "no duplicates")
        assertTrue(cells.all { it.zoom == FogGrid.STORAGE_ZOOM })
    }

    @Test
    fun `stroke covers both ends and is gap-free`() {
        // ~100 m east along the same street.
        val from = moscow
        val to = GeoPoint(moscow.latitude, moscow.longitude + 0.0016)
        val radius = 15.0
        val stroke = FogBrush.strokeCells(from, to, radius).toSet()

        assertTrue(stroke.containsAll(FogBrush.diskCells(from, radius)))
        assertTrue(stroke.containsAll(FogBrush.diskCells(to, radius)))

        // Every column of cells between the end points is revealed in the centre row.
        val start = FogGrid.cellAt(from)
        val end = FogGrid.cellAt(to)
        for (x in start.x..end.x) {
            assertTrue(stroke.any { it.x == x && it.y == start.y }, "gap at column $x")
        }
        assertTrue(stroke.size > FogBrush.diskCells(from, radius).size * 2)
    }

    @Test
    fun `strokes are 4-connected at storage and display zoom`() {
        val random = Random(2024)
        repeat(300) {
            val from = GeoPoint(moscow.latitude + random.nextDouble(-0.002, 0.002), moscow.longitude + random.nextDouble(-0.004, 0.004))
            val to = GeoPoint(from.latitude + random.nextDouble(-0.0015, 0.0015), from.longitude + random.nextDouble(-0.003, 0.003))
            val radius = listOf(0.0, 5.0, 12.0, 20.0, 35.0)[random.nextInt(5)]
            val cells = FogBrush.strokeCells(from, to, radius).toSet()
            assertTrue(cells.contains(FogGrid.cellAt(from)) && cells.contains(FogGrid.cellAt(to)))
            assertTrue(isFourConnected(cells), "storage cells not 4-connected: $from -> $to r=$radius")
            assertTrue(isFourConnected(cells.map { it.ancestor(18) }.toSet()), "display cells not 4-connected: $from -> $to r=$radius")
        }
    }

    @Test
    fun `exact diagonal through cell corners never leaves corner-only contacts`() {
        // Corners of storage cells: pick a cell corner in world units and go diagonally to another corner.
        val origin = FogGrid.tileOrigin(FogGrid.cellAt(moscow))
        val side = 1.0 / (1 shl FogGrid.STORAGE_ZOOM)
        val from = Epsg3395.toGeo(WorldPoint(origin.x + 1e-12, origin.y + 1e-12))
        val to = Epsg3395.toGeo(WorldPoint(origin.x + 7 * side + 1e-12, origin.y + 7 * side + 1e-12))
        val cells = FogBrush.strokeCells(from, to, 0.0).toSet()
        assertTrue(isFourConnected(cells))
        assertTrue(cells.size >= 15, "diagonal of 8 cells needs ≥ 15 cells to be 4-connected, got ${cells.size}")
    }

    private fun isFourConnected(cells: Set<TileKey>): Boolean {
        if (cells.isEmpty()) return true
        val seen = HashSet<TileKey>()
        val queue = ArrayDeque<TileKey>().apply { add(cells.first()) }
        seen += cells.first()
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val n = TileKey.of(c.zoom, c.x + dx, c.y + dy)
                if (n in cells && seen.add(n)) queue += n
            }
        }
        return seen.size == cells.size
    }

    @Test
    fun `brush clamps at the edge of the world`() {
        val corner = GeoPoint(-84.9, 179.999)
        val cells = FogBrush.diskCells(corner, 500.0)
        val max = (1 shl FogGrid.STORAGE_ZOOM) - 1
        assertTrue(cells.all { it.x in 0..max && it.y in 0..max })
        assertTrue(cells.isNotEmpty())
    }
}
