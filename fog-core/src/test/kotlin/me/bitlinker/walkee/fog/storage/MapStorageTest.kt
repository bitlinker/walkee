package me.bitlinker.walkee.fog.storage

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.TileKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class MapStorageTest {

    // A 2×2 block of z20 cells that fits inside one z19 tile and one z18 tile.
    private val baseX = 633_856
    private val baseY = 328_712
    private val block = listOf(
        TileKey.of(20, baseX, baseY),
        TileKey.of(20, baseX + 1, baseY),
        TileKey.of(20, baseX, baseY + 1),
        TileKey.of(20, baseX + 1, baseY + 1),
    )
    private val chunkKey = block.first().ancestor(FogGrid.CHUNK_ZOOM)

    private fun newStorage(store: ChunkStore = InMemoryChunkStore()) = MapStorage(store)

    @Test
    fun `marks cells and counts them at every zoom`() {
        val storage = newStorage()
        val change = storage.markVisited(block + TileKey.of(20, baseX + 40, baseY))

        assertEquals(setOf(chunkKey), change.chunks)
        assertEquals(5, change.addedCells)
        assertEquals(5L, storage.visitedCellCount)
        assertEquals(1, storage.chunkCount)

        assertTrue(storage.isVisited(block[0]))
        assertFalse(storage.isVisited(TileKey.of(20, baseX + 2, baseY)))

        assertEquals(1L, storage.visitedCount(block[0]))
        assertEquals(1L, storage.visitedCount(block[0].child(3)), "above storage zoom the enclosing cell decides")
        assertEquals(4L, storage.visitedCount(block[0].ancestor(19)))
        assertEquals(4L, storage.visitedCount(block[0].ancestor(18)))
        assertEquals(5L, storage.visitedCount(block[0].ancestor(14)))
        assertEquals(5L, storage.visitedCount(chunkKey))
        assertEquals(5L, storage.visitedCount(chunkKey.ancestor(5)))
        assertEquals(5L, storage.visitedCount(TileKey.ROOT))
        assertEquals(0L, storage.visitedCount(TileKey.of(18, 0, 0)))
    }

    @Test
    fun `marking already visited cells is a no-op`() {
        val storage = newStorage()
        storage.markVisited(block)
        val again = storage.markVisited(block)
        assertTrue(again.isEmpty)
        assertEquals(ChangeSet.EMPTY, again)
        assertEquals(4L, storage.visitedCellCount)
        assertTrue(storage.markVisited(emptyList()).isEmpty)
    }

    @Test
    fun `rejects non-cell keys`() {
        val storage = newStorage()
        assertThrows(IllegalArgumentException::class.java) { storage.markVisited(listOf(TileKey.of(18, 0, 0))) }
        assertThrows(IllegalArgumentException::class.java) { storage.isVisited(TileKey.of(19, 0, 0)) }
        assertThrows(IllegalArgumentException::class.java) { storage.chunk(TileKey.of(11, 0, 0)) }
    }

    @Test
    fun `changes flow emits every effective write`() = runTest {
        val storage = newStorage()
        storage.changes.test {
            storage.markVisited(block)
            val first = awaitItem()
            assertEquals(setOf(chunkKey), first.chunks)
            assertEquals(4, first.addedCells)

            storage.markVisited(block) // no-op → nothing emitted
            val farAway = TileKey.of(20, 100, 100)
            storage.markVisited(listOf(block[0], farAway))
            val second = awaitItem()
            assertEquals(setOf(farAway.ancestor(FogGrid.CHUNK_ZOOM)), second.chunks)
            assertEquals(1, second.addedCells)
            expectNoEvents()
        }
    }

    @Test
    fun `chunksWithin scans the Morton range`() {
        val storage = newStorage()
        val neighbourChunkCell = TileKey.of(20, baseX + FogGrid.CHUNK_SIDE, baseY)
        val farCell = TileKey.of(20, 100, 100)
        storage.markVisited(listOf(block[0], neighbourChunkCell, farCell))
        assertEquals(3, storage.chunkCount)

        val neighbourChunk = neighbourChunkCell.ancestor(FogGrid.CHUNK_ZOOM)
        val common = chunkKey.ancestor(10)
        assertTrue(neighbourChunk in common)

        assertEquals(setOf(chunkKey, neighbourChunk), storage.chunksWithin(common).map { it.key }.toSet())
        assertEquals(3, storage.chunksWithin(TileKey.ROOT).size)
        assertEquals(listOf(chunkKey), storage.chunksWithin(block[0].ancestor(15)).map { it.key })
        assertEquals(listOf(chunkKey), storage.chunksWithin(block[0]).map { it.key })
        assertEquals(listOf(farCell.ancestor(FogGrid.CHUNK_ZOOM)), storage.chunksWithin(TileKey.of(10, 0, 0)).map { it.key })
        assertTrue(storage.chunksWithin(TileKey.of(10, 512, 512)).isEmpty())
    }

    @Test
    fun `flush writes dirty chunks and load restores them`(@TempDir dir: File) {
        val storage = newStorage(FileChunkStore(dir))
        assertEquals(0, storage.flush())
        storage.markVisited(block + TileKey.of(20, 100, 100))
        assertTrue(storage.hasUnsavedChanges)

        assertEquals(2, storage.flush())
        assertFalse(storage.hasUnsavedChanges)
        assertEquals(0, storage.flush())
        assertEquals(2, dir.listFiles { f -> f.name.endsWith(".chunk") }!!.size)

        storage.markVisited(listOf(TileKey.of(20, baseX + 7, baseY)))
        assertEquals(1, storage.flush(), "only the changed chunk is rewritten")

        val restored = newStorage(FileChunkStore(dir))
        val result = restored.load()
        assertTrue(result.failures.isEmpty())
        assertEquals(2, result.chunks.size)
        assertEquals(6L, restored.visitedCellCount)
        assertTrue(restored.isVisited(block[3]))
        assertTrue(restored.isVisited(TileKey.of(20, baseX + 7, baseY)))
        assertTrue(restored.isVisited(TileKey.of(20, 100, 100)))
        assertEquals(5L, restored.visitedCount(chunkKey))
        assertEquals(6L, restored.visitedCount(TileKey.ROOT))
        assertFalse(restored.hasUnsavedChanges)
    }

    @Test
    fun `corrupt chunk files are skipped and quarantined`(@TempDir dir: File) {
        val storage = newStorage(FileChunkStore(dir))
        storage.markVisited(block)
        storage.flush()
        File(dir, "deadbeef00000000.chunk").writeBytes(ByteArray(40) { 1 })

        val restored = newStorage(FileChunkStore(dir))
        val result = restored.load()
        assertEquals(1, result.chunks.size)
        assertEquals(1, result.failures.size)
        assertEquals("deadbeef00000000.chunk", result.failures.single().source)
        assertTrue(File(dir, "deadbeef00000000.chunk.corrupt").exists())
        assertFalse(File(dir, "deadbeef00000000.chunk").exists())
        assertEquals(4L, restored.visitedCellCount)
    }

    @Test
    fun `failed flush keeps chunks dirty`() {
        val failing = object : ChunkStore {
            override fun loadAll() = ChunkStore.LoadResult(emptyList(), emptyList())
            override fun save(chunks: Collection<Chunk>) = throw IOException("disk full")
            override fun deleteAll() = Unit
        }
        val storage = newStorage(failing)
        storage.markVisited(block)
        assertThrows(IOException::class.java) { storage.flush() }
        assertTrue(storage.hasUnsavedChanges)
    }

    @Test
    fun `clear forgets everything in memory and on disk`(@TempDir dir: File) {
        val storage = newStorage(FileChunkStore(dir))
        storage.markVisited(block)
        storage.flush()
        storage.markVisited(listOf(TileKey.of(20, 100, 100))) // unsaved
        File(dir, "deadbeef00000000.chunk.corrupt").writeBytes(ByteArray(4))
        File(dir, "unrelated.txt").writeText("keep")

        storage.clear()
        assertEquals(0L, storage.visitedCellCount)
        assertEquals(0, storage.chunkCount)
        assertEquals(0L, storage.visitedCount(TileKey.ROOT))
        assertFalse(storage.isVisited(block[0]))
        assertFalse(storage.hasUnsavedChanges)
        assertEquals(0, storage.flush())
        assertEquals(listOf("unrelated.txt"), dir.list()!!.toList())

        val restored = newStorage(FileChunkStore(dir))
        assertTrue(restored.load().chunks.isEmpty())

        // Still usable afterwards.
        storage.markVisited(block)
        assertEquals(4L, storage.visitedCellCount)
        assertEquals(1, storage.flush())
    }

    @Test
    fun `failed clear keeps every cell and rewrites it on the next flush`() {
        val store = object : ChunkStore {
            val saved = ArrayList<Chunk>()
            var failDelete = true
            override fun loadAll() = ChunkStore.LoadResult(emptyList(), emptyList())
            override fun save(chunks: Collection<Chunk>) { saved += chunks }
            override fun deleteAll() { if (failDelete) throw IOException("read-only") }
        }
        val storage = newStorage(store)
        storage.markVisited(block)
        storage.flush()
        store.saved.clear()

        assertThrows(IOException::class.java) { storage.clear() }
        assertEquals(4L, storage.visitedCellCount)
        assertTrue(storage.hasUnsavedChanges)
        assertEquals(1, storage.flush())
        assertEquals(listOf(chunkKey), store.saved.map { it.key })
    }

    @Test
    fun `load replaces in-memory state`() {
        val store = InMemoryChunkStore()
        val first = newStorage(store)
        first.markVisited(block)
        first.flush()

        val second = newStorage(store)
        second.markVisited(listOf(TileKey.of(20, 100, 100)))
        second.load()
        assertEquals(4L, second.visitedCellCount)
        assertFalse(second.isVisited(TileKey.of(20, 100, 100)))
        assertFalse(second.hasUnsavedChanges)
    }
}
