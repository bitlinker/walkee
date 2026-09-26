package me.bitlinker.walkee.data.map

import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.HexKey
import me.bitlinker.walkee.fog.geo.HexLattice
import me.bitlinker.walkee.fog.geo.TileKey
import me.bitlinker.walkee.fog.storage.MapStorage
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Hexagonal cells (ADR 0003): the window of [lattice] hexagons that points of [tile] can fall into
 * and which of them are open. A hexagon is open when at least one storage cell whose centre lies
 * in it was visited (ADR 0001).
 */
class HexCoverage(
    val tile: TileKey,
    val lattice: HexLattice,
    val firstRow: Int,
    val firstCol: Int,
    val rows: Int,
    val cols: Int,
    /** Row-major bits of the window, `rows × cols`. */
    private val openBits: LongArray,
) : TileCoverage {
    init {
        require(rows > 0 && cols > 0) { "Empty window $rows × $cols" }
        require(openBits.size == wordCount(rows * cols)) {
            "Expected ${wordCount(rows * cols)} words, got ${openBits.size}"
        }
    }

    val openCount: Int = openBits.sumOf { java.lang.Long.bitCount(it) }

    override val isAllClosed: Boolean get() = openCount == 0

    override val isAllOpen: Boolean get() = openCount == rows * cols

    operator fun contains(hex: HexKey): Boolean =
        hex.row - firstRow in 0 until rows && hex.col - firstCol in 0 until cols

    /** Whether [hex] is open; hexagons outside the window count as closed. */
    fun isOpen(hex: HexKey): Boolean {
        if (hex !in this) return false
        val index = (hex.row - firstRow) * cols + (hex.col - firstCol)
        return (openBits[index ushr 6] ushr (index and 63)) and 1L != 0L
    }

    /** FNV-1a over the window and its bits, see [TileCoverage.fingerprint]. */
    override fun fingerprint(): Long {
        val hash = Fnv1a()
        hash.mix(lattice.displayZoom)
        hash.mix(firstRow)
        hash.mix(firstCol)
        hash.mix(rows)
        hash.mix(cols)
        for (word in openBits) hash.mix(word)
        return hash.value
    }

    companion object {
        fun wordCount(bits: Int): Int = (bits + 63) ushr 6
    }
}

/** Builds [HexCoverage] for a tile from [MapStorage] primitives; runs on renderer threads. */
object HexCoverageBuilder {
    private const val WORLD_CELLS = 1 shl FogGrid.STORAGE_ZOOM
    private const val WORLD_CHUNKS = 1 shl FogGrid.CHUNK_ZOOM

    fun build(storage: MapStorage, tile: TileKey, lattice: HexLattice): HexCoverage {
        val tileSide = Math.scalb(1.0, FogGrid.STORAGE_ZOOM - tile.zoom)
        val tileX = tile.x * tileSide
        val tileY = tile.y * tileSide
        val rowRange = lattice.rowRange(tileY, tileY + tileSide)
        val colRange = lattice.columnRange(tileX, tileX + tileSide)
        val firstRow = rowRange.first
        val firstCol = colRange.first
        val rows = rowRange.last - firstRow + 1
        val cols = colRange.last - firstCol + 1
        val bits = LongArray(HexCoverage.wordCount(rows * cols))

        // Storage cells whose centres can lie in the window's hexagons: a hexagon reaches half a
        // width east and west of its centre (odd rows are shifted half a width east) and
        // halfHeight north and south. Columns stay unwrapped; chunks are looked up wrapped.
        val fromX = floor(firstCol * lattice.width - lattice.width / 2).toInt()
        val toX = ceil(colRange.last * lattice.width + lattice.width).toInt()
        val fromY = max(0, floor(firstRow * lattice.rowSpacing - lattice.halfHeight).toInt())
        val toY = min(WORLD_CELLS, ceil(rowRange.last * lattice.rowSpacing + lattice.halfHeight).toInt())

        if (fromY < toY) {
            for (chunkY in (fromY shr FogGrid.CHUNK_SHIFT)..((toY - 1) shr FogGrid.CHUNK_SHIFT)) {
                for (chunkX in Math.floorDiv(fromX, FogGrid.CHUNK_SIDE)..Math.floorDiv(toX - 1, FogGrid.CHUNK_SIDE)) {
                    val key = TileKey.of(FogGrid.CHUNK_ZOOM, Math.floorMod(chunkX, WORLD_CHUNKS), chunkY)
                    val chunk = storage.chunk(key) ?: continue
                    val baseX = chunkX * FogGrid.CHUNK_SIDE
                    val baseY = chunkY * FogGrid.CHUNK_SIDE
                    chunk.cells.forEachSetBit(
                        max(fromX - baseX, 0), max(fromY - baseY, 0),
                        min(toX - baseX, FogGrid.CHUNK_SIDE), min(toY - baseY, FogGrid.CHUNK_SIDE),
                    ) { x, y ->
                        val hex = lattice.hexAt(baseX + x + 0.5, baseY + y + 0.5)
                        val row = hex.row - firstRow
                        val col = hex.col - firstCol
                        if (row in 0 until rows && col in 0 until cols) {
                            val index = row * cols + col
                            bits[index ushr 6] = bits[index ushr 6] or (1L shl (index and 63))
                        }
                    }
                }
            }
        }
        return HexCoverage(tile, lattice, firstRow, firstCol, rows, cols, bits)
    }
}
