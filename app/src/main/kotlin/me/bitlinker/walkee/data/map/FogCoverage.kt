package me.bitlinker.walkee.data.map

import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.TileKey
import me.bitlinker.walkee.fog.storage.MapStorage
import kotlin.math.max
import kotlin.math.min

/**
 * What a map tile should show: a `side × side` grid where each cell holds the number of *open
 * display cells* it contains, out of [capacity]. A display cell is open when at least one of its
 * storage cells was visited (ADR 0001).
 *
 * - Tiles at or above the display zoom: `side = 1`, `capacity = 1`.
 * - Tiles up to 8 zooms below the display zoom: one grid cell per display cell, `capacity = 1`.
 * - Further out: grid cells are `2^k` display cells wide and the counts become densities.
 */
class FogCoverage(
    val side: Int,
    val openCounts: IntArray,
    val capacity: Int,
) {
    init {
        require(openCounts.size == side * side) { "Expected ${side * side} counts, got ${openCounts.size}" }
    }

    val isAllClosed: Boolean get() = openCounts.all { it == 0 }

    val isAllOpen: Boolean get() = openCounts.all { it == capacity }

    /** Fraction of open display cells in grid cell [index], `0f..1f`. */
    fun openness(index: Int): Float = openCounts[index].toFloat() / capacity
}

/** Builds [FogCoverage] for a tile from [MapStorage] primitives; runs on renderer threads. */
object FogCoverageBuilder {
    /** Tiles are 256 px, so a grid finer than `256 × 256` would be sub-pixel. */
    private const val MAX_SIDE_SHIFT = 8

    fun build(storage: MapStorage, tile: TileKey, displayZoom: Int): FogCoverage {
        require(displayZoom in FogGrid.CHUNK_ZOOM..FogGrid.STORAGE_ZOOM) {
            "Display zoom must be in ${FogGrid.CHUNK_ZOOM}..${FogGrid.STORAGE_ZOOM}, got $displayZoom"
        }
        val blockShift = FogGrid.STORAGE_ZOOM - displayZoom

        if (tile.zoom >= displayZoom) {
            val open = storage.visitedCount(tile.ancestor(displayZoom)) > 0
            return FogCoverage(1, intArrayOf(if (open) 1 else 0), 1)
        }

        val gridZoom = min(displayZoom, tile.zoom + MAX_SIDE_SHIFT)
        val sideShift = gridZoom - tile.zoom
        val side = 1 shl sideShift
        val counts = IntArray(side * side)
        val displayPerGridShift = displayZoom - gridZoom
        val capacity = 1 shl (2 * displayPerGridShift)

        val chunks = storage.chunksWithin(tile)
        if (chunks.isEmpty()) return FogCoverage(side, counts, capacity)

        val gridX0 = tile.x shl sideShift
        val gridY0 = tile.y shl sideShift

        if (gridZoom <= FogGrid.CHUNK_ZOOM) {
            // Grid cells are whole chunks or larger: add each chunk's open display cells.
            val chunkToGridShift = FogGrid.CHUNK_ZOOM - gridZoom
            for (chunk in chunks) {
                val gx = (chunk.key.x ushr chunkToGridShift) - gridX0
                val gy = (chunk.key.y ushr chunkToGridShift) - gridY0
                counts[gy * side + gx] += chunk.occupancy(blockShift).cardinality
            }
        } else {
            // Grid cells lie inside chunks: count open display cells block by block.
            val gridPerChunkShift = gridZoom - FogGrid.CHUNK_ZOOM
            val blockSize = 1 shl displayPerGridShift
            for (chunk in chunks) {
                val occupancy = chunk.occupancy(blockShift)
                val chunkGridX0 = chunk.key.x shl gridPerChunkShift
                val chunkGridY0 = chunk.key.y shl gridPerChunkShift
                val fromX = max(chunkGridX0, gridX0)
                val toX = min(chunkGridX0 + (1 shl gridPerChunkShift), gridX0 + side)
                val fromY = max(chunkGridY0, gridY0)
                val toY = min(chunkGridY0 + (1 shl gridPerChunkShift), gridY0 + side)
                for (gy in fromY until toY) {
                    val localY = (gy - chunkGridY0) shl displayPerGridShift
                    val row = (gy - gridY0) * side
                    for (gx in fromX until toX) {
                        val localX = (gx - chunkGridX0) shl displayPerGridShift
                        counts[row + (gx - gridX0)] = occupancy.countInBlock(localX, localY, blockSize)
                    }
                }
            }
        }
        return FogCoverage(side, counts, capacity)
    }
}
