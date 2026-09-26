package me.bitlinker.walkee.fog.storage

import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.TileKey
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.random.Random

class ChunkCodecTest {

    private val key = TileKey.of(FogGrid.CHUNK_ZOOM, 2476, 1284)

    @Test
    fun `partial chunk round trips`() {
        val random = Random(3)
        val chunk = Chunk.empty(key).plus(IntArray(500) { random.nextInt(FogGrid.CHUNK_CELLS) })
        val bytes = ChunkCodec.encode(chunk)
        val decoded = ChunkCodec.decode(bytes)

        assertEquals(chunk.key, decoded.key)
        assertEquals(chunk.visitedCount, decoded.visitedCount)
        assertArrayEquals(chunk.copyWords(), decoded.copyWords())
    }

    @Test
    fun `sparse street-like chunk compresses well`() {
        // A few "streets": full rows and columns.
        val indices = ArrayList<Int>()
        for (i in 0 until FogGrid.CHUNK_SIDE) {
            indices += FogGrid.localIndex(i, 100)
            indices += FogGrid.localIndex(100, i)
            indices += FogGrid.localIndex(i, i)
        }
        val chunk = Chunk.empty(key).plus(indices.toIntArray())
        val bytes = ChunkCodec.encode(chunk)
        assertTrue(bytes.size < 1024, "expected < 1 KB, got ${bytes.size}")
        assertEquals(chunk.visitedCount, ChunkCodec.decode(bytes).visitedCount)
    }

    @Test
    fun `full chunk is stored without a bitmap`() {
        val bytes = ChunkCodec.encode(Chunk.full(key))
        assertTrue(bytes.size < 32, "expected a header only, got ${bytes.size} bytes")
        val decoded = ChunkCodec.decode(bytes)
        assertTrue(decoded.isFull)
        assertEquals(key, decoded.key)
    }

    @Test
    fun `rejects garbage and wrong magic`() {
        assertThrows(IOException::class.java) { ChunkCodec.decode(ByteArray(0)) }
        assertThrows(IOException::class.java) { ChunkCodec.decode(ByteArray(64) { 0x41 }) }
        val valid = ChunkCodec.encode(Chunk.empty(key).plus(intArrayOf(1, 2, 3)))
        val truncated = valid.copyOf(valid.size - 5)
        assertThrows(IOException::class.java) { ChunkCodec.decode(truncated) }
    }

    @Test
    fun `earlier format versions are obsolete, later ones unsupported`() {
        val bytes = ChunkCodec.encode(Chunk.empty(key).plus(intArrayOf(1, 2, 3)))
        bytes[4] = 1
        assertThrows(ObsoleteChunkException::class.java) { ChunkCodec.decode(bytes) }
        bytes[4] = 3
        val error = assertThrows(IOException::class.java) { ChunkCodec.decode(bytes) }
        assertTrue(error !is ObsoleteChunkException)
    }

    @Test
    fun `detects a bitmap that disagrees with the header count`() {
        val bytes = ChunkCodec.encode(Chunk.empty(key).plus(intArrayOf(1, 2, 3)))
        // visitedCount is the i32 right after magic(4) + version(1) + key(8).
        bytes[13 + 3] = 9
        assertThrows(IOException::class.java) { ChunkCodec.decode(bytes) }
    }
}
