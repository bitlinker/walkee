package me.bitlinker.walkee.fog.geo

/**
 * Layout of the fog-of-war grid on top of the EPSG:3395 tile pyramid (ADR 0001, 0002).
 *
 * - A *storage cell* is a tile at [STORAGE_ZOOM] (z21, ~10–14 m across Russia); one bit each.
 * - A *chunk* is a tile at [CHUNK_ZOOM] (z13), i.e. a `256 × 256` square of storage cells.
 *
 * The zoom at which cells are *displayed* (z18 in the first version) is a rendering parameter
 * and deliberately not part of the storage layout.
 */
object FogGrid {
    const val STORAGE_ZOOM = 21
    const val CHUNK_ZOOM = 13

    /** `log2` of a chunk side measured in storage cells. */
    const val CHUNK_SHIFT = STORAGE_ZOOM - CHUNK_ZOOM

    /** Chunk side in storage cells (256). */
    const val CHUNK_SIDE = 1 shl CHUNK_SHIFT

    /** Storage cells per chunk (65 536). */
    const val CHUNK_CELLS = CHUNK_SIDE * CHUNK_SIDE

    private const val LOCAL_MASK = CHUNK_SIDE - 1

    /** Tile of the pyramid at [zoom] containing a normalized world position. */
    fun tileAt(world: WorldPoint, zoom: Int): TileKey {
        val side = 1 shl zoom
        val x = (world.x * side).toInt().coerceIn(0, side - 1)
        val y = (world.y * side).toInt().coerceIn(0, side - 1)
        return TileKey.of(zoom, x, y)
    }

    fun tileAt(point: GeoPoint, zoom: Int): TileKey = tileAt(Epsg3395.toWorld(point), zoom)

    /** Storage cell containing a geographic point. */
    fun cellAt(point: GeoPoint): TileKey = tileAt(point, STORAGE_ZOOM)

    fun chunkOf(cell: TileKey): TileKey = cell.ancestor(CHUNK_ZOOM)

    /** Row-major index of a storage cell inside its chunk, `0 until CHUNK_CELLS`. */
    fun localIndex(cell: TileKey): Int {
        require(cell.zoom == STORAGE_ZOOM) { "Expected a storage cell (z$STORAGE_ZOOM), got $cell" }
        return localIndex(cell.x and LOCAL_MASK, cell.y and LOCAL_MASK)
    }

    fun localIndex(localX: Int, localY: Int): Int = (localY shl CHUNK_SHIFT) or localX

    /** Storage cell of a chunk by its local index. */
    fun cellOf(chunk: TileKey, localIndex: Int): TileKey {
        require(chunk.zoom == CHUNK_ZOOM) { "Expected a chunk (z$CHUNK_ZOOM), got $chunk" }
        val x = (chunk.x shl CHUNK_SHIFT) or (localIndex and LOCAL_MASK)
        val y = (chunk.y shl CHUNK_SHIFT) or (localIndex ushr CHUNK_SHIFT)
        return TileKey.of(STORAGE_ZOOM, x, y)
    }

    /** North-west corner of a tile in normalized world coordinates. */
    fun tileOrigin(tile: TileKey): WorldPoint {
        val side = (1 shl tile.zoom).toDouble()
        return WorldPoint(tile.x / side, tile.y / side)
    }

    /** Geographic centre of a tile. */
    fun tileCenter(tile: TileKey): GeoPoint {
        val side = (1 shl tile.zoom).toDouble()
        return Epsg3395.toGeo(WorldPoint((tile.x + 0.5) / side, (tile.y + 0.5) / side))
    }

    /** Ground size of a storage cell side in metres at a given latitude. */
    fun cellSizeMetres(latitude: Double): Double = Epsg3395.tileSizeMetres(latitude, STORAGE_ZOOM)
}
