package me.bitlinker.walkee.data.map

import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.TileKey
import me.bitlinker.walkee.fog.storage.InMemoryChunkStore
import me.bitlinker.walkee.fog.storage.MapStorage
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class FogCoverageBuilderTest {

    private val displayZoom = 18
    private val storage = MapStorage(InMemoryChunkStore())

    /** `log2` of a display cell side in storage cells. */
    private val cellShift = FogGrid.STORAGE_ZOOM - displayZoom

    /** One display cell (z18) at Moscow's centre, i.e. an 8×8 block of storage cells. */
    private val displayCell = TileKey.of(18, 158_464, 82_178)
    private val cellsInDisplayCell = (0 until (1 shl cellShift)).flatMap { dy ->
        (0 until (1 shl cellShift)).map { dx -> cell((displayCell.x shl cellShift) + dx, (displayCell.y shl cellShift) + dy) }
    }

    private fun cell(x: Int, y: Int) = TileKey.of(FogGrid.STORAGE_ZOOM, x, y)

    @Test
    fun `empty storage yields all-closed coverage`() {
        val coverage = FogCoverageBuilder.build(storage, TileKey.of(14, 9904, 5136), displayZoom)
        assertEquals(16, coverage.side)
        assertEquals(1, coverage.capacity)
        assertTrue(coverage.isAllClosed)
        assertFalse(coverage.isAllOpen)
    }

    @Test
    fun `a single visited storage cell opens its whole display cell`() {
        storage.markVisited(listOf(cellsInDisplayCell[5]))

        val atDisplayZoom = FogCoverageBuilder.build(storage, displayCell, displayZoom)
        assertEquals(1, atDisplayZoom.side)
        assertArrayEquals(intArrayOf(1), atDisplayZoom.openCounts)
        assertTrue(atDisplayZoom.isAllOpen)

        // Any tile inside the display cell — even an unvisited storage cell — is open.
        val insideUnvisited = FogCoverageBuilder.build(storage, cellsInDisplayCell[0], displayZoom)
        assertTrue(insideUnvisited.isAllOpen)
        val neighbourDisplayCell = TileKey.of(18, displayCell.x + 1, displayCell.y)
        assertTrue(FogCoverageBuilder.build(storage, neighbourDisplayCell, displayZoom).isAllClosed)
    }

    @Test
    fun `tiles below the display zoom map one grid cell per display cell`() {
        storage.markVisited(cellsInDisplayCell.take(3))

        val parent = FogCoverageBuilder.build(storage, displayCell.parent(), displayZoom)
        assertEquals(2, parent.side)
        assertEquals(1, parent.capacity)
        val quadrant = displayCell.x and 1 or ((displayCell.y and 1) shl 1)
        assertEquals(1, parent.openCounts.sum())
        assertEquals(1, parent.openCounts[quadrant])

        val z10 = FogCoverageBuilder.build(storage, displayCell.ancestor(10), displayZoom)
        assertEquals(256, z10.side)
        assertEquals(1, z10.capacity)
        assertEquals(1, z10.openCounts.sum())
        val gx = displayCell.x - (displayCell.ancestor(10).x shl 8)
        val gy = displayCell.y - (displayCell.ancestor(10).y shl 8)
        assertEquals(1, z10.openCounts[gy * 256 + gx])
    }

    @Test
    fun `far-out tiles report densities against the right capacity`() {
        storage.markVisited(cellsInDisplayCell)
        val other = TileKey.of(18, displayCell.x + 3, displayCell.y + 2) // same z16 tile, another display cell
        storage.markVisited(listOf(cell(other.x shl cellShift, other.y shl cellShift)))

        val z9 = FogCoverageBuilder.build(storage, displayCell.ancestor(9), displayZoom)
        assertEquals(256, z9.side)
        assertEquals(4, z9.capacity)
        assertEquals(2, z9.openCounts.sum())

        // Grid cells are chunks (z13 == chunk zoom): 4^(18-13) display cells each.
        val z5 = FogCoverageBuilder.build(storage, displayCell.ancestor(5), displayZoom)
        assertEquals(256, z5.side)
        assertEquals(1 shl 10, z5.capacity)
        assertEquals(2, z5.openCounts.sum())

        // Grid cells are 2×2 chunks.
        val z4 = FogCoverageBuilder.build(storage, displayCell.ancestor(4), displayZoom)
        assertEquals(1 shl 12, z4.capacity)
        assertEquals(2, z4.openCounts.sum())

        val root = FogCoverageBuilder.build(storage, TileKey.ROOT, displayZoom)
        assertEquals(2, root.openCounts.sum())
        assertEquals(1 shl 20, root.capacity)
    }

    @Test
    fun `chunk boundaries inside a tile are handled`() {
        // Two display cells in adjacent chunks, both inside one z11 tile.
        val chunk = displayCell.ancestor(FogGrid.CHUNK_ZOOM)
        val nextChunk = TileKey.of(FogGrid.CHUNK_ZOOM, chunk.x + 1, chunk.y)
        val a = cell(chunk.x shl FogGrid.CHUNK_SHIFT or FogGrid.CHUNK_SIDE - 1, chunk.y shl FogGrid.CHUNK_SHIFT)
        val b = cell(nextChunk.x shl FogGrid.CHUNK_SHIFT, nextChunk.y shl FogGrid.CHUNK_SHIFT)
        storage.markVisited(listOf(a, b))
        assertEquals(2, storage.chunkCount)

        val tile = chunk.ancestor(11)
        assertTrue(nextChunk in tile)
        val coverage = FogCoverageBuilder.build(storage, tile, displayZoom)
        assertEquals(128, coverage.side)
        assertEquals(1, coverage.capacity)
        assertEquals(2, coverage.openCounts.sum())
    }

    @Test
    fun `fingerprint changes exactly when coverage changes`() {
        val tile = displayCell.ancestor(14)
        val before = FogCoverageBuilder.build(storage, tile, displayZoom)
        assertEquals(before.fingerprint(), FogCoverageBuilder.build(storage, tile, displayZoom).fingerprint())

        storage.markVisited(listOf(cellsInDisplayCell[0]))
        val after = FogCoverageBuilder.build(storage, tile, displayZoom)
        assertTrue(before.fingerprint() != after.fingerprint())

        // Another storage cell inside the same display cell does not change what is shown.
        storage.markVisited(listOf(cellsInDisplayCell[1]))
        assertEquals(after.fingerprint(), FogCoverageBuilder.build(storage, tile, displayZoom).fingerprint())

        // Same counts but a different grid layout must not collide.
        assertTrue(FogCoverage(1, intArrayOf(0), 1).fingerprint() != FogCoverage(2, IntArray(4), 1).fingerprint())
    }

    @Test
    fun `margin holds the display cells around the tile, across chunk boundaries`() {
        // Visited cells scattered around a chunk corner, so margins reach into up to four chunks.
        val chunk = displayCell.ancestor(FogGrid.CHUNK_ZOOM)
        val cornerX = (chunk.x + 1) shl FogGrid.CHUNK_SHIFT
        val cornerY = (chunk.y + 1) shl FogGrid.CHUNK_SHIFT
        val random = Random(11)
        storage.markVisited(List(400) { cell(cornerX + random.nextInt(-96, 96), cornerY + random.nextInt(-96, 96)) })
        val corner = cell(cornerX, cornerY)

        for (zoom in listOf(10, 13, 16, 17, 18, 19, 20, 21)) {
            val southEast = corner.ancestor(zoom)
            for (tile in listOf(southEast, TileKey.of(zoom, southEast.x - 1, southEast.y - 1))) {
                val coverage = FogCoverageBuilder.build(storage, tile, displayZoom, margin = 2)
                val ownX = if (zoom >= displayZoom) tile.x shr (zoom - displayZoom) else tile.x shl (displayZoom - zoom)
                val ownY = if (zoom >= displayZoom) tile.y shr (zoom - displayZoom) else tile.y shl (displayZoom - zoom)
                assertEquals(ownX - 2, coverage.originX, "$tile")
                assertEquals(ownY - 2, coverage.originY, "$tile")
                for (y in -2 until coverage.side + 2) {
                    for (x in -2 until coverage.side + 2) {
                        val cell = TileKey.of(displayZoom, ownX + x, ownY + y)
                        val expected = if (storage.visitedCount(cell) > 0) 1 else 0
                        assertEquals(expected, coverage.count(x, y), "$tile, cell ($x, $y)")
                    }
                }
            }
        }
    }

    @Test
    fun `tiles above the display zoom know where they lie inside their cell`() {
        val tile = TileKey.of(20, (displayCell.x shl 2) + 3, (displayCell.y shl 2) + 1)
        val coverage = FogCoverageBuilder.build(storage, tile, displayZoom, margin = 2)
        assertEquals(1, coverage.side)
        assertEquals(5, coverage.stride)
        assertEquals(2, coverage.subdivision)
        assertEquals(3, coverage.subX)
        assertEquals(1, coverage.subY)
    }

    @Test
    fun `margins wrap across the antimeridian and stay closed beyond the poles`() {
        storage.markVisited(listOf(cell((1 shl FogGrid.STORAGE_ZOOM) - 1, 0)))
        val coverage = FogCoverageBuilder.build(storage, TileKey.of(displayZoom, 0, 0), displayZoom, margin = 2)
        assertEquals(1, coverage.count(-1, 0))
        assertEquals(1, coverage.openCounts.sum())
    }

    @Test
    fun `margin cells take part in the fingerprint`() {
        fun fingerprint(margin: Int) = FogCoverageBuilder.build(storage, displayCell, displayZoom, margin).fingerprint()
        val before = fingerprint(2)
        val withoutMargin = fingerprint(0)

        storage.markVisited(listOf(cell((displayCell.x + 1) shl cellShift, displayCell.y shl cellShift)))
        assertTrue(before != fingerprint(2))
        assertEquals(withoutMargin, fingerprint(0))
    }

    @Test
    fun `margins are rejected for density grids`() {
        assertThrows(IllegalArgumentException::class.java) {
            FogCoverageBuilder.build(storage, displayCell.ancestor(9), displayZoom, margin = 2)
        }
    }

    @Test
    fun `rejects display zoom outside the chunk-to-storage range`() {
        assertThrows(IllegalArgumentException::class.java) { FogCoverageBuilder.build(storage, TileKey.ROOT, FogGrid.CHUNK_ZOOM - 1) }
        assertThrows(IllegalArgumentException::class.java) { FogCoverageBuilder.build(storage, TileKey.ROOT, FogGrid.STORAGE_ZOOM + 1) }
    }
}
