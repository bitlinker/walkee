package me.bitlinker.walkee.fog.storage

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.TileKey
import java.util.concurrent.ConcurrentSkipListMap

/**
 * The fog-of-war data structure (ADR 0002): a sparse bitmap of visited storage cells kept as
 * immutable [Chunk] snapshots in a Morton-ordered map, plus an [AggregateIndex] of counts for
 * zooms above the chunk level.
 *
 * Threading: reads are lock-free and safe from any thread (map tile renderers call them from
 * MapKit worker threads); writes are serialized internally and cheap (copy-on-write of one 8 KB
 * chunk). [load] and [flush] block on I/O — run them on an I/O dispatcher.
 *
 * Cells can only be marked visited, never cleared; that keeps aggregates and cross-device merges
 * trivial (bitwise OR).
 */
class MapStorage(private val store: ChunkStore) {

    private val chunks = ConcurrentSkipListMap<Long, Chunk>()
    private val aggregates = AggregateIndex()
    private val dirtyChunks = LinkedHashSet<Long>()
    private val writeLock = Any()

    private val _changes = MutableSharedFlow<ChangeSet>(
        extraBufferCapacity = CHANGES_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Every non-empty write, for renderer invalidation and progress tracking. */
    val changes: SharedFlow<ChangeSet> = _changes.asSharedFlow()

    /** Total number of visited storage cells. */
    val visitedCellCount: Long get() = aggregates.total

    val chunkCount: Int get() = chunks.size

    val hasUnsavedChanges: Boolean get() = synchronized(writeLock) { dirtyChunks.isNotEmpty() }

    fun isVisited(cell: TileKey): Boolean {
        require(cell.zoom == FogGrid.STORAGE_ZOOM) { "Expected a storage cell, got $cell" }
        val chunk = chunks[FogGrid.chunkOf(cell).packed] ?: return false
        return chunk.get(FogGrid.localIndex(cell))
    }

    /** Current snapshot of a chunk, or `null` if nothing in it was visited. */
    fun chunk(key: TileKey): Chunk? {
        require(key.zoom == FogGrid.CHUNK_ZOOM) { "Expected a chunk key, got $key" }
        return chunks[key.packed]
    }

    /**
     * Snapshots of all non-empty chunks intersecting [tile]: a single chunk for tiles at or below
     * the chunk zoom, a Morton range scan above it.
     */
    fun chunksWithin(tile: TileKey): List<Chunk> {
        if (tile.zoom >= FogGrid.CHUNK_ZOOM) {
            return listOfNotNull(chunks[tile.ancestor(FogGrid.CHUNK_ZOOM).packed])
        }
        val range = tile.descendantRange(FogGrid.CHUNK_ZOOM)
        return chunks.subMap(range.first, true, range.last, true).values.toList()
    }

    /**
     * Number of visited storage cells inside [tile] at any zoom. Above the storage zoom the tile
     * lies inside one cell and the result is `1` if that cell is visited.
     */
    fun visitedCount(tile: TileKey): Long {
        if (tile.zoom <= FogGrid.CHUNK_ZOOM) return aggregates.count(tile)
        val chunk = chunks[tile.ancestor(FogGrid.CHUNK_ZOOM).packed] ?: return 0L
        if (tile.zoom >= FogGrid.STORAGE_ZOOM) {
            return if (chunk.get(FogGrid.localIndex(tile.ancestor(FogGrid.STORAGE_ZOOM)))) 1L else 0L
        }
        val shift = FogGrid.STORAGE_ZOOM - tile.zoom
        val localMask = FogGrid.CHUNK_SIDE - 1
        return chunk.countInBlock((tile.x shl shift) and localMask, (tile.y shl shift) and localMask, 1 shl shift).toLong()
    }

    /** Marks storage cells visited. Returns what changed (possibly [ChangeSet.EMPTY]) and emits it to [changes]. */
    fun markVisited(cells: Iterable<TileKey>): ChangeSet {
        val indicesByChunk = HashMap<Long, MutableList<Int>>()
        for (cell in cells) {
            require(cell.zoom == FogGrid.STORAGE_ZOOM) { "Expected a storage cell, got $cell" }
            indicesByChunk.getOrPut(FogGrid.chunkOf(cell).packed) { ArrayList() } += FogGrid.localIndex(cell)
        }
        if (indicesByChunk.isEmpty()) return ChangeSet.EMPTY

        val changed = HashSet<TileKey>()
        var added = 0
        synchronized(writeLock) {
            for ((packed, indices) in indicesByChunk) {
                val key = TileKey(packed)
                val current = chunks[packed] ?: Chunk.empty(key)
                val updated = current.plus(indices.toIntArray())
                if (updated === current) continue
                chunks[packed] = updated
                val delta = updated.visitedCount - current.visitedCount
                aggregates.add(key, delta)
                added += delta
                changed += key
                dirtyChunks += packed
            }
        }
        if (changed.isEmpty()) return ChangeSet.EMPTY
        val changeSet = ChangeSet(changed, added)
        _changes.tryEmit(changeSet)
        return changeSet
    }

    /** Replaces in-memory state with the store's contents. Blocking. */
    fun load(): ChunkStore.LoadResult {
        val result = store.loadAll()
        synchronized(writeLock) {
            chunks.clear()
            aggregates.clear()
            dirtyChunks.clear()
            for (chunk in result.chunks) {
                chunks[chunk.key.packed] = chunk
                aggregates.add(chunk.key, chunk.visitedCount)
            }
        }
        return result
    }

    /**
     * Writes all chunks changed since the last flush. Blocking. Returns the number of chunks
     * written; on failure the chunks stay dirty and the exception propagates.
     */
    fun flush(): Int {
        val snapshot: List<Chunk>
        synchronized(writeLock) {
            if (dirtyChunks.isEmpty()) return 0
            snapshot = dirtyChunks.mapNotNull { chunks[it] }
            dirtyChunks.clear()
        }
        try {
            store.save(snapshot)
        } catch (e: Exception) {
            synchronized(writeLock) { for (chunk in snapshot) dirtyChunks += chunk.key.packed }
            throw e
        }
        return snapshot.size
    }

    private companion object {
        const val CHANGES_BUFFER = 256
    }
}
