package me.bitlinker.walkee.data.map

import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.GeoPoint
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
    fun `brush clamps at the edge of the world`() {
        val corner = GeoPoint(-84.9, 179.999)
        val cells = FogBrush.diskCells(corner, 500.0)
        val max = (1 shl FogGrid.STORAGE_ZOOM) - 1
        assertTrue(cells.all { it.x in 0..max && it.y in 0..max })
        assertTrue(cells.isNotEmpty())
    }
}
