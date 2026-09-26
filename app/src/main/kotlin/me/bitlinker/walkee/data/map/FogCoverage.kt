package me.bitlinker.walkee.data.map

import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.TileKey
import me.bitlinker.walkee.fog.storage.MapStorage
import kotlin.math.max
import kotlin.math.min

/** What a map tile should show; built by the data layer, drawn by a painter in `ui/map`. */
sealed interface TileCoverage {
    /** Nothing in the tile is revealed. */
    val isAllClosed: Boolean

    /** Everything in the tile is revealed. */
    val isAllOpen: Boolean

    /**
     * 64-bit hash of what the tile shows: equal coverages always match, different ones collide
     * with negligible probability. Used as the tile etag so unchanged tiles are not re-rendered.
     */
    fun fingerprint(): Long
}

/**
 * Square cells: a grid where each cell holds the number of *open display cells* it contains, out
 * of [capacity]. A display cell is open when at least one of its storage cells was visited
 * (ADR 0001).
 *
 * - Tiles at or above the display zoom: `side = 1`; the tile lies inside one display cell and is
 *   `2^subdivision` times smaller than it, at position ([subX], [subY]) inside it.
 * - Tiles up to 8 zooms below the display zoom: one grid cell per display cell, `capacity = 1`.
 * - Further out: grid cells are `2^k` display cells wide and the counts become densities.
 *
 * Painters that look across tile edges (soft fog, ADR 0003) ask for a [margin]: that many extra
 * cells of context on every side, so [openCounts] is a row-major `stride × stride` grid with the
 * tile's own cells at `margin until margin + side`. Margins exist only for display-cell grids.
 */
class FogCoverage(
    /** Grid cells across the tile itself, `≥ 1`. */
    val side: Int,
    val openCounts: IntArray,
    val capacity: Int,
    val margin: Int = 0,
    val subdivision: Int = 0,
    val subX: Int = 0,
    val subY: Int = 0,
    /** Global grid coordinates (at the grid's own zoom) of the first padded cell; anchors world-space effects. */
    val originX: Int = 0,
    val originY: Int = 0,
) : TileCoverage {
    /** Row length of [openCounts]: the tile's cells plus the margin on both sides. */
    val stride: Int get() = side + 2 * margin

    init {
        require(margin >= 0) { "Margin must be non-negative, got $margin" }
        require(openCounts.size == stride * stride) { "Expected ${stride * stride} counts, got ${openCounts.size}" }
        require(subdivision == 0 || side == 1) { "Only single-cell tiles can be smaller than a cell" }
        require(subX in 0 until (1 shl subdivision) && subY in 0 until (1 shl subdivision)) {
            "Sub-cell position ($subX, $subY) is outside a 2^$subdivision grid"
        }
    }

    /** True when every cell, margin included, is closed. */
    override val isAllClosed: Boolean get() = openCounts.all { it == 0 }

    /** True when every cell, margin included, is fully open. */
    override val isAllOpen: Boolean get() = openCounts.all { it == capacity }

    /** Open display cells in the cell at ([x], [y]) relative to the tile's first own cell; `-margin until side + margin`. */
    fun count(x: Int, y: Int): Int = openCounts[(y + margin) * stride + x + margin]

    /** Fraction of open display cells in the cell at ([x], [y]), `0f..1f`; coordinates as in [count]. */
    fun openness(x: Int, y: Int): Float = count(x, y).toFloat() / capacity

    /** FNV-1a over the grid, margin included, see [TileCoverage.fingerprint]. */
    override fun fingerprint(): Long {
        val hash = Fnv1a()
        hash.mix(side)
        hash.mix(margin)
        hash.mix(capacity)
        for (count in openCounts) hash.mix(count)
        return hash.value
    }
}

/** 64-bit FNV-1a over 32-bit values. */
internal class Fnv1a {
    var value: Long = OFFSET
        private set

    fun mix(item: Int) {
        value = (value xor (item.toLong() and 0xFFFFFFFFL)) * PRIME
    }

    fun mix(item: Long) {
        mix(item.toInt())
        mix((item ushr 32).toInt())
    }

    private companion object {
        const val OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        const val PRIME = 0x100000001b3L
    }
}

/** Builds [FogCoverage] for a tile from [MapStorage] primitives; runs on renderer threads. */
object FogCoverageBuilder {
    /** Tiles are 256 px, so a grid finer than `256 × 256` would be sub-pixel. */
    private const val MAX_SIDE_SHIFT = 8

    /**
     * Coverage of [tile] with display cells at [displayZoom] and [margin] cells of context around
     * it. A margin needs one grid cell per display cell: `tile.zoom ≥ displayZoom − 8`.
     */
    fun build(storage: MapStorage, tile: TileKey, displayZoom: Int, margin: Int = 0): FogCoverage {
        require(displayZoom in FogGrid.CHUNK_ZOOM..FogGrid.STORAGE_ZOOM) {
            "Display zoom must be in ${FogGrid.CHUNK_ZOOM}..${FogGrid.STORAGE_ZOOM}, got $displayZoom"
        }
        require(margin >= 0) { "Margin must be non-negative, got $margin" }
        if (tile.zoom >= displayZoom - MAX_SIDE_SHIFT) return buildDisplayGrid(storage, tile, displayZoom, margin)
        require(margin == 0) { "Margins need one grid cell per display cell; tile $tile is too far out for z$displayZoom" }
        return buildDensityGrid(storage, tile, displayZoom)
    }

    /**
     * One grid cell per display cell. Looks chunks up directly over the padded window, so the
     * margin may reach into neighbouring chunks; it wraps across the antimeridian, and rows beyond
     * the pyramid's latitude cut-off stay closed.
     */
    private fun buildDisplayGrid(storage: MapStorage, tile: TileKey, displayZoom: Int, margin: Int): FogCoverage {
        val subdivision = max(0, tile.zoom - displayZoom)
        val sideShift = max(0, displayZoom - tile.zoom)
        val side = 1 shl sideShift
        val stride = side + 2 * margin
        val originX = (tile.x shl sideShift shr subdivision) - margin
        val originY = (tile.y shl sideShift shr subdivision) - margin
        val counts = IntArray(stride * stride)

        val blockShift = FogGrid.STORAGE_ZOOM - displayZoom
        val chunkShift = displayZoom - FogGrid.CHUNK_ZOOM
        val chunkSide = 1 shl chunkShift
        val chunksPerRow = 1 shl FogGrid.CHUNK_ZOOM
        // Arithmetic shifts floor negative coordinates, so the margin left of x = 0 maps to chunk −1.
        for (chunkY in (originY shr chunkShift)..((originY + stride - 1) shr chunkShift)) {
            if (chunkY !in 0 until chunksPerRow) continue
            for (chunkX in (originX shr chunkShift)..((originX + stride - 1) shr chunkShift)) {
                val chunk = storage.chunk(TileKey.of(FogGrid.CHUNK_ZOOM, chunkX.mod(chunksPerRow), chunkY)) ?: continue
                val occupancy = chunk.occupancy(blockShift)
                val chunkX0 = chunkX shl chunkShift
                val chunkY0 = chunkY shl chunkShift
                val fromX = max(chunkX0, originX)
                val toX = min(chunkX0 + chunkSide, originX + stride)
                for (gy in max(chunkY0, originY) until min(chunkY0 + chunkSide, originY + stride)) {
                    val row = (gy - originY) * stride - originX
                    for (gx in fromX until toX) {
                        if (occupancy[gx - chunkX0, gy - chunkY0]) counts[row + gx] = 1
                    }
                }
            }
        }
        val subMask = (1 shl subdivision) - 1
        return FogCoverage(side, counts, 1, margin, subdivision, tile.x and subMask, tile.y and subMask, originX, originY)
    }

    /** `256 × 256` grid whose cells are `2^k` display cells wide; counts are densities. */
    private fun buildDensityGrid(storage: MapStorage, tile: TileKey, displayZoom: Int): FogCoverage {
        val blockShift = FogGrid.STORAGE_ZOOM - displayZoom
        val gridZoom = tile.zoom + MAX_SIDE_SHIFT
        val side = 1 shl MAX_SIDE_SHIFT
        val counts = IntArray(side * side)
        val displayPerGridShift = displayZoom - gridZoom
        val capacity = 1 shl (2 * displayPerGridShift)
        val gridX0 = tile.x shl MAX_SIDE_SHIFT
        val gridY0 = tile.y shl MAX_SIDE_SHIFT

        val chunks = storage.chunksWithin(tile)
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
        return FogCoverage(side, counts, capacity, originX = gridX0, originY = gridY0)
    }
}
