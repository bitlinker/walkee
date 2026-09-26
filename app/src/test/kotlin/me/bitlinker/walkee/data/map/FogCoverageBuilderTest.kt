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

class FogCoverageBuilderTest {

    private val displayZoom = 18
    private val storage = MapStorage(InMemoryChunkStore())

    /** One display cell (z18) at Moscow's centre, i.e. a 4×4 block of storage cells. */
    private val displayCell = TileKey.of(18, 158_464, 82_178)
    private val cellsInDisplayCell = (0 until 4).flatMap { dy ->
        (0 until 4).map { dx -> TileKey.of(20, (displayCell.x shl 2) + dx, (displayCell.y shl 2) + dy) }
    }

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
        storage.markVisited(listOf(TileKey.of(20, other.x shl 2, other.y shl 2)))

        val z9 = FogCoverageBuilder.build(storage, displayCell.ancestor(9), displayZoom)
        assertEquals(256, z9.side)
        assertEquals(4, z9.capacity)
        assertEquals(2, z9.openCounts.sum())

        // Grid cells are chunks (z12 == chunk zoom): 4^(18-12) display cells each.
        val z4 = FogCoverageBuilder.build(storage, displayCell.ancestor(4), displayZoom)
        assertEquals(256, z4.side)
        assertEquals(1 shl 12, z4.capacity)
        assertEquals(2, z4.openCounts.sum())

        // Grid cells are 2×2 chunks.
        val z3 = FogCoverageBuilder.build(storage, displayCell.ancestor(3), displayZoom)
        assertEquals(1 shl 14, z3.capacity)
        assertEquals(2, z3.openCounts.sum())

        val root = FogCoverageBuilder.build(storage, TileKey.ROOT, displayZoom)
        assertEquals(2, root.openCounts.sum())
        assertEquals(1 shl 20, root.capacity)
    }

    @Test
    fun `chunk boundaries inside a tile are handled`() {
        // Two display cells in adjacent chunks, both inside one z11 tile.
        val chunk = displayCell.ancestor(FogGrid.CHUNK_ZOOM)
        val nextChunk = TileKey.of(FogGrid.CHUNK_ZOOM, chunk.x + 1, chunk.y)
        val a = TileKey.of(20, chunk.x shl 8 or 255, chunk.y shl 8)
        val b = TileKey.of(20, nextChunk.x shl 8, nextChunk.y shl 8)
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
    fun `rejects display zoom outside the chunk-to-storage range`() {
        assertThrows(IllegalArgumentException::class.java) { FogCoverageBuilder.build(storage, TileKey.ROOT, 11) }
        assertThrows(IllegalArgumentException::class.java) { FogCoverageBuilder.build(storage, TileKey.ROOT, 21) }
    }
}
