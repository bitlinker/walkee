package me.bitlinker.walkee.fog.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TileKeyTest {

    @Test
    fun `packs and unpacks zoom and coordinates`() {
        val random = Random(42)
        repeat(10_000) {
            val zoom = random.nextInt(TileKey.MAX_ZOOM + 1)
            val side = 1 shl zoom
            val x = random.nextInt(side)
            val y = random.nextInt(side)
            val key = TileKey.of(zoom, x, y)
            assertEquals(zoom, key.zoom)
            assertEquals(x, key.x)
            assertEquals(y, key.y)
        }
        val corner = TileKey.of(TileKey.MAX_ZOOM, (1 shl TileKey.MAX_ZOOM) - 1, (1 shl TileKey.MAX_ZOOM) - 1)
        assertEquals((1 shl TileKey.MAX_ZOOM) - 1, corner.x)
        assertEquals((1 shl TileKey.MAX_ZOOM) - 1, corner.y)
        assertTrue(corner.packed > 0, "keys must stay positive so Long ordering matches zoom ordering")
    }

    @Test
    fun `quadrants follow the x-then-y bit convention`() {
        val root = TileKey.ROOT
        assertEquals(TileKey.of(1, 0, 0), root.child(0))
        assertEquals(TileKey.of(1, 1, 0), root.child(1))
        assertEquals(TileKey.of(1, 0, 1), root.child(2))
        assertEquals(TileKey.of(1, 1, 1), root.child(3))
        assertEquals(root.children(), listOf(root.child(0), root.child(1), root.child(2), root.child(3)))
    }

    @Test
    fun `parent and ancestor shift coordinates`() {
        val cell = TileKey.of(20, 633_856, 328_712)
        assertEquals(TileKey.of(19, 316_928, 164_356), cell.parent())
        assertEquals(TileKey.of(12, 2476, 1284), cell.ancestor(12))
        assertEquals(TileKey.ROOT, cell.ancestor(0))
        assertEquals(cell, cell.ancestor(20))

        var walked = cell
        repeat(8) { walked = walked.parent() }
        assertEquals(cell.ancestor(12), walked)
        assertThrows(IllegalArgumentException::class.java) { TileKey.ROOT.parent() }
        assertThrows(IllegalArgumentException::class.java) { cell.ancestor(21) }
    }

    @Test
    fun `children are inside parent and vice versa`() {
        val tile = TileKey.of(5, 17, 9)
        for (child in tile.children()) {
            assertEquals(tile, child.parent())
            assertTrue(child in tile)
            assertFalse(tile in child)
        }
        assertTrue(tile in tile)
        assertFalse(TileKey.of(5, 18, 9) in tile)
    }

    @Test
    fun `descendant range is exactly the set of descendants`() {
        val tile = TileKey.of(3, 5, 2)
        val range = tile.descendantRange(6)
        assertEquals(64L, range.last - range.first + 1)
        for (x in 0 until 64) {
            for (y in 0 until 64) {
                val candidate = TileKey.of(6, x, y)
                val expected = x shr 3 == 5 && y shr 3 == 2
                assertEquals(expected, candidate.packed in range, "tile $candidate")
                assertEquals(expected, candidate in tile, "tile $candidate")
            }
        }
        assertEquals(tile.packed..tile.packed, tile.descendantRange(3))
    }

    @Test
    fun `ordering puts descendants of one tile in a contiguous block`() {
        val sorted = (0 until 16).flatMap { x -> (0 until 16).map { y -> TileKey.of(4, x, y) } }.sorted()
        val parents = sorted.map { it.ancestor(2) }
        // Each parent's descendants appear as one run — the parent sequence never revisits a value.
        val runs = parents.zipWithNext().count { (a, b) -> a != b } + 1
        assertEquals(16, runs)
    }

    @Test
    fun `rejects tiles outside the world`() {
        assertThrows(IllegalArgumentException::class.java) { TileKey.of(3, 8, 0) }
        assertThrows(IllegalArgumentException::class.java) { TileKey.of(3, 0, -1) }
        assertThrows(IllegalArgumentException::class.java) { TileKey.of(30, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { TileKey.ROOT.child(4) }
    }

    @Test
    fun `renders as zoom slash x slash y`() {
        assertEquals("18/158464/82178", TileKey.of(18, 158_464, 82_178).toString())
    }
}
