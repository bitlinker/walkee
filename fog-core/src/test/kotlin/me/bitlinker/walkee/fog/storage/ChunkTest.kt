package me.bitlinker.walkee.fog.storage

import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.TileKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ChunkTest {

    private val key = TileKey.of(FogGrid.CHUNK_ZOOM, 2476, 1284)

    @Test
    fun `empty chunk has nothing set`() {
        val chunk = Chunk.empty(key)
        assertTrue(chunk.isEmpty)
        assertFalse(chunk.isFull)
        assertEquals(0, chunk.visitedCount)
        assertFalse(chunk[0, 0])
        assertEquals(0, chunk.countInBlock(0, 0, FogGrid.CHUNK_SIDE))
    }

    @Test
    fun `plus marks cells once and leaves the original untouched`() {
        val original = Chunk.empty(key)
        val updated = original.plus(intArrayOf(0, 5, FogGrid.CHUNK_CELLS - 1, 5))

        assertNotSame(original, updated)
        assertEquals(3, updated.visitedCount)
        assertTrue(updated.get(0))
        assertTrue(updated.get(5))
        assertTrue(updated[255, 255])
        assertFalse(updated.get(6))
        assertTrue(original.isEmpty)

        assertSame(updated, updated.plus(intArrayOf(0, 5)), "no new cells → same snapshot")
        assertSame(updated, updated.plus(IntArray(0)))
    }

    @Test
    fun `full chunk`() {
        val chunk = Chunk.full(key)
        assertTrue(chunk.isFull)
        assertEquals(FogGrid.CHUNK_CELLS, chunk.countInBlock(0, 0, FogGrid.CHUNK_SIDE))
        assertEquals(16, chunk.countInBlock(64, 128, 4))
    }

    @Test
    fun `countInBlock matches brute force for every block size`() {
        val random = Random(7)
        val indices = IntArray(3000) { random.nextInt(FogGrid.CHUNK_CELLS) }
        val chunk = Chunk.empty(key).plus(indices)
        assertEquals(indices.distinct().size, chunk.visitedCount)

        for (shift in 0..FogGrid.CHUNK_SHIFT) {
            val size = 1 shl shift
            repeat(50) {
                val x = random.nextInt(FogGrid.CHUNK_SIDE / size) * size
                val y = random.nextInt(FogGrid.CHUNK_SIDE / size) * size
                var expected = 0
                for (dy in 0 until size) for (dx in 0 until size) if (chunk[x + dx, y + dy]) expected++
                assertEquals(expected, chunk.countInBlock(x, y, size), "block ($x, $y) size $size")
            }
        }
    }

    @Test
    fun `countInBlock validates alignment and bounds`() {
        val chunk = Chunk.empty(key)
        assertThrows(IllegalArgumentException::class.java) { chunk.countInBlock(1, 0, 2) }
        assertThrows(IllegalArgumentException::class.java) { chunk.countInBlock(0, 0, 3) }
        assertThrows(IllegalArgumentException::class.java) { chunk.countInBlock(0, 0, 512) }
        assertThrows(IllegalArgumentException::class.java) { chunk.countInBlock(192, 0, 128) }
    }

    @Test
    fun `fromWords counts bits and rejects wrong keys`() {
        val words = LongArray(Chunk.WORDS)
        words[3] = 0b1011L
        words[Chunk.WORDS - 1] = Long.MIN_VALUE
        val chunk = Chunk.fromWords(key, words)
        assertEquals(4, chunk.visitedCount)
        assertTrue(chunk.get(3 * 64))
        assertTrue(chunk.get(FogGrid.CHUNK_CELLS - 1))

        assertThrows(IllegalArgumentException::class.java) { Chunk.empty(TileKey.of(11, 0, 0)) }
        assertThrows(IllegalArgumentException::class.java) { Chunk.fromWords(key, LongArray(10)) }
    }
}
