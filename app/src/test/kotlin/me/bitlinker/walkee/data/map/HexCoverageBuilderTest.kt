package me.bitlinker.walkee.data.map

import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.HexKey
import me.bitlinker.walkee.fog.geo.HexLattice
import me.bitlinker.walkee.fog.geo.TileKey
import me.bitlinker.walkee.fog.storage.InMemoryChunkStore
import me.bitlinker.walkee.fog.storage.MapStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class HexCoverageBuilderTest {

    private val lattice = HexLattice(18)
    private val storage = MapStorage(InMemoryChunkStore())
    private val worldCells = 1 shl FogGrid.STORAGE_ZOOM

    /** Moscow's centre (see FogGridTest). */
    private val cell = TileKey.of(20, 633_856, 328_712)

    private fun hexOf(cell: TileKey): HexKey = lattice.hexAt(cell.x + 0.5, cell.y + 0.5)

    private fun build(tile: TileKey) = HexCoverageBuilder.build(storage, tile, lattice)

    @Test
    fun `empty storage yields all-closed coverage`() {
        val coverage = build(cell.ancestor(14))
        assertTrue(coverage.isAllClosed)
        assertFalse(coverage.isAllOpen)
        // A z14 tile is 64 storage cells: 16 hexagons wide and ~18 rows tall, plus partly covered
        // ones along the edges.
        assertEquals(18, coverage.cols)
        assertEquals(20, coverage.rows)
    }

    @Test
    fun `a single visited cell opens exactly its hexagon in every tile that shows it`() {
        storage.markVisited(listOf(cell))
        val hex = hexOf(cell)
        for (zoom in 12..23) {
            val tile = if (zoom <= 20) cell.ancestor(zoom) else tileAtCentre(cell, zoom)
            val coverage = build(tile)
            assertTrue(coverage.isOpen(hex), "z$zoom")
            assertEquals(1, coverage.openCount, "z$zoom")
        }
    }

    @Test
    fun `cells of a neighbouring chunk open the hexagons they share with the tile`() {
        val chunk = cell.ancestor(FogGrid.CHUNK_ZOOM)
        // First column of the chunk to the east; find a row whose hexagon reaches back into [chunk].
        val eastX = (chunk.x + 1) shl FogGrid.CHUNK_SHIFT
        val y = (0 until 16).map { (chunk.y shl FogGrid.CHUNK_SHIFT) + 100 + it }
            .first { lattice.hexAt(eastX - 0.5, it + 0.5) == lattice.hexAt(eastX + 0.5, it + 0.5) }
        val outside = TileKey.of(20, eastX, y)
        storage.markVisited(listOf(outside))
        assertFalse(outside in chunk)

        val coverage = build(chunk)
        assertTrue(coverage.isOpen(hexOf(outside)))
        assertEquals(1, coverage.openCount)
    }

    @Test
    fun `hexagons straddling the antimeridian open on both sides`() {
        // An even row's column `columns` is centred exactly on the antimeridian.
        val y = (328_700 until 328_720).first { hexAtCell(worldCells - 1, it).col == lattice.columns }
        storage.markVisited(listOf(TileKey.of(20, worldCells - 1, y)))
        val row = hexAtCell(worldCells - 1, y).row

        val west = build(TileKey.of(18, 0, y shr 2))
        assertTrue(west.isOpen(HexKey.of(row, 0)))
        assertEquals(1, west.openCount)
        val east = build(TileKey.of(18, (1 shl 18) - 1, y shr 2))
        assertTrue(east.isOpen(HexKey.of(row, lattice.columns)))
        assertEquals(1, east.openCount)
    }

    @Test
    fun `every tile agrees with hexagons opened by brute force`() {
        val random = Random(1)
        val originX = cell.x - 300
        val originY = cell.y - 300
        val visited = List(400) { TileKey.of(20, originX + random.nextInt(600), originY + random.nextInt(600)) }
        storage.markVisited(visited)
        val open = visited.map(::hexOf).toSet()

        for (zoom in listOf(12, 13, 15, 17, 18, 19, 20)) {
            val tile = cell.ancestor(zoom)
            for (dy in -1..1) for (dx in -1..1) {
                val coverage = build(TileKey.of(zoom, tile.x + dx, tile.y + dy))
                var expectedOpen = 0
                for (row in coverage.firstRow until coverage.firstRow + coverage.rows) {
                    for (col in coverage.firstCol until coverage.firstCol + coverage.cols) {
                        val hex = HexKey.of(row, col)
                        assertEquals(hex in open, coverage.isOpen(hex), "z$zoom tile ($dx, $dy) $hex")
                        if (hex in open) expectedOpen++
                    }
                }
                assertEquals(expectedOpen, coverage.openCount)
            }
        }
    }

    @Test
    fun `fingerprint changes exactly when a hexagon opens`() {
        val tile = cell.ancestor(15)
        val before = build(tile)
        assertEquals(before.fingerprint(), build(tile).fingerprint())

        storage.markVisited(listOf(cell))
        val after = build(tile)
        assertTrue(before.fingerprint() != after.fingerprint())

        // Another storage cell of the same hexagon does not change what is shown.
        val sibling = (-1..1).flatMap { dy -> (-1..1).map { dx -> TileKey.of(20, cell.x + dx, cell.y + dy) } }
            .first { it != cell && hexOf(it) == hexOf(cell) }
        storage.markVisited(listOf(sibling))
        assertEquals(after.fingerprint(), build(tile).fingerprint())
    }

    @Test
    fun `tiles at the edge of the pyramid stay within the world`() {
        storage.markVisited(listOf(TileKey.of(20, 5, 0), TileKey.of(20, 7, worldCells - 1)))
        assertEquals(1, build(TileKey.of(18, 1, 0)).openCount)
        assertEquals(1, build(TileKey.of(18, 1, (1 shl 18) - 1)).openCount)
    }

    private fun hexAtCell(x: Int, y: Int): HexKey = lattice.hexAt(x + 0.5, y + 0.5)

    /** The tile at [zoom] (> 20) whose north-west corner is the centre of [cell]. */
    private fun tileAtCentre(cell: TileKey, zoom: Int): TileKey {
        var tile = cell.child(3)
        while (tile.zoom < zoom) tile = tile.child(0)
        return tile
    }
}
