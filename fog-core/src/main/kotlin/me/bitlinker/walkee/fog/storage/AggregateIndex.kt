package me.bitlinker.walkee.fog.storage

import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.TileKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Number of visited storage cells inside every tile from zoom 0 down to [FogGrid.CHUNK_ZOOM]
 * ("summed quadtree", ADR 0002). Lets low-zoom tiles and progress counters be served without
 * touching chunk bitmaps. Writes are expected to be serialized by the owner; reads are lock-free.
 */
internal class AggregateIndex {
    private val counts = ConcurrentHashMap<Long, Long>()

    /** Adds [delta] cells to the chunk and all of its ancestors up to the root. */
    fun add(chunk: TileKey, delta: Int) {
        require(chunk.zoom == FogGrid.CHUNK_ZOOM) { "Expected a chunk key, got $chunk" }
        if (delta == 0) return
        var key = chunk
        while (true) {
            counts.merge(key.packed, delta.toLong()) { a, b -> a + b }
            if (key.zoom == 0) return
            key = key.parent()
        }
    }

    fun count(tile: TileKey): Long {
        require(tile.zoom <= FogGrid.CHUNK_ZOOM) { "Aggregates exist only down to zoom ${FogGrid.CHUNK_ZOOM}, got $tile" }
        return counts[tile.packed] ?: 0L
    }

    val total: Long get() = counts[TileKey.ROOT.packed] ?: 0L

    fun clear() = counts.clear()
}
