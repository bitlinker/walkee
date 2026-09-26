package me.bitlinker.walkee.fog.storage

import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.TileKey

/**
 * Immutable snapshot of one chunk: a `256 × 256` [BitGrid] of visited storage cells (ADR 0002).
 *
 * Instances never change; [plus] returns a new chunk (copy-on-write), which lets renderer threads
 * read snapshots without any locking. Coarser [occupancy] grids (e.g. "which 4×4 blocks — display
 * cells — contain at least one visited cell") are derived lazily and cached per snapshot.
 */
class Chunk private constructor(
    val key: TileKey,
    val cells: BitGrid,
) {
    init {
        require(key.zoom == FogGrid.CHUNK_ZOOM) { "Chunk key must be at zoom ${FogGrid.CHUNK_ZOOM}, got $key" }
        require(cells.sideShift == FogGrid.CHUNK_SHIFT) { "Chunk grid must have side shift ${FogGrid.CHUNK_SHIFT}, got ${cells.sideShift}" }
    }

    @Volatile
    private var occupancyCache: Array<BitGrid?> = arrayOfNulls(FogGrid.CHUNK_SHIFT + 1)

    /** Number of visited cells in the chunk. */
    val visitedCount: Int get() = cells.cardinality

    val isEmpty: Boolean get() = cells.isEmpty

    val isFull: Boolean get() = cells.isFull

    operator fun get(localX: Int, localY: Int): Boolean = cells[localX, localY]

    fun get(localIndex: Int): Boolean = cells.get(localIndex)

    /** See [BitGrid.countInBlock]. */
    fun countInBlock(localX: Int, localY: Int, size: Int): Int = cells.countInBlock(localX, localY, size)

    /**
     * Grid of `256 >> blockShift` per side telling which aligned `2^blockShift` blocks of storage
     * cells contain at least one visited cell. With `blockShift = STORAGE_ZOOM − displayZoom` this
     * is the set of *open display cells* in the chunk. Cached; cheap to call repeatedly.
     */
    fun occupancy(blockShift: Int): BitGrid {
        if (blockShift == 0) return cells
        occupancyCache[blockShift]?.let { return it }
        val computed = cells.occupancy(blockShift)
        occupancyCache[blockShift] = computed
        return computed
    }

    /** A chunk with the given cells (local indices) additionally marked visited; `this` if none was new. */
    fun plus(localIndices: IntArray): Chunk {
        val updated = cells.plus(localIndices)
        return if (updated === cells) this else Chunk(key, updated)
    }

    /** Defensive copy of the raw bitmap, for serialization. */
    fun copyWords(): LongArray = cells.copyWords()

    companion object {
        /** Words per chunk bitmap (1024). */
        const val WORDS = FogGrid.CHUNK_CELLS / 64

        fun empty(key: TileKey): Chunk = Chunk(key, BitGrid.empty(FogGrid.CHUNK_SHIFT))

        fun full(key: TileKey): Chunk = Chunk(key, BitGrid.full(FogGrid.CHUNK_SHIFT))

        /** Wraps [words] (taken over, not copied) and counts its bits. */
        fun fromWords(key: TileKey, words: LongArray): Chunk = Chunk(key, BitGrid.fromWords(FogGrid.CHUNK_SHIFT, words))
    }
}
