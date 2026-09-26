package me.bitlinker.walkee.fog.geo

/**
 * Identifier of a tile (or a storage cell — a cell is just a tile at [FogGrid.STORAGE_ZOOM]) in
 * the Yandex EPSG:3395 tile pyramid, packed into one `Long`:
 *
 * ```
 * bits 63..58  zoom (0..29)
 * bits 57..0   Morton code of (x, y)
 * ```
 *
 * Because of the Morton layout the packed keys of all descendants of a tile at any deeper zoom
 * form a contiguous range ([descendantRange]), which turns "everything inside tile T" into a
 * range scan over a sorted map. Keys are totally ordered by zoom first, then by Z-order.
 *
 * Quadrants of [child] are numbered `0` north-west, `1` north-east, `2` south-west, `3` south-east
 * (bit 0 is the x bit, bit 1 the y bit).
 */
@JvmInline
value class TileKey(val packed: Long) : Comparable<TileKey> {

    val zoom: Int get() = (packed ushr ZOOM_SHIFT).toInt()

    val x: Int get() = Morton.decodeX(morton)

    val y: Int get() = Morton.decodeY(morton)

    private val morton: Long get() = packed and MORTON_MASK

    fun parent(): TileKey {
        require(zoom > 0) { "Root tile has no parent" }
        return TileKey(((zoom - 1).toLong() shl ZOOM_SHIFT) or (morton ushr 2))
    }

    /** The tile at [targetZoom] (`<= zoom`) that contains this one; `this` when zooms are equal. */
    fun ancestor(targetZoom: Int): TileKey {
        require(targetZoom in 0..zoom) { "Ancestor zoom $targetZoom is not in 0..$zoom" }
        return TileKey((targetZoom.toLong() shl ZOOM_SHIFT) or (morton ushr (2 * (zoom - targetZoom))))
    }

    fun child(quadrant: Int): TileKey {
        require(quadrant in 0..3) { "Quadrant must be 0..3, got $quadrant" }
        require(zoom < MAX_ZOOM) { "Cannot descend below zoom $MAX_ZOOM" }
        return TileKey(((zoom + 1).toLong() shl ZOOM_SHIFT) or (morton shl 2) or quadrant.toLong())
    }

    fun children(): List<TileKey> = List(4) { child(it) }

    /** Packed keys of all descendants at [targetZoom] (`>= zoom`), inclusive on both ends. */
    fun descendantRange(targetZoom: Int): LongRange {
        require(targetZoom in zoom..MAX_ZOOM) { "Descendant zoom $targetZoom is not in $zoom..$MAX_ZOOM" }
        val shift = 2 * (targetZoom - zoom)
        val first = (targetZoom.toLong() shl ZOOM_SHIFT) or (morton shl shift)
        return first..(first + (1L shl shift) - 1)
    }

    /** True when [other] is this tile or lies inside it. */
    operator fun contains(other: TileKey): Boolean = other.zoom >= zoom && other.ancestor(zoom) == this

    override fun compareTo(other: TileKey): Int = packed.compareTo(other.packed)

    override fun toString(): String = "$zoom/$x/$y"

    companion object {
        const val MAX_ZOOM = 29

        private const val ZOOM_SHIFT = 58
        private const val MORTON_MASK = (1L shl ZOOM_SHIFT) - 1

        fun of(zoom: Int, x: Int, y: Int): TileKey {
            require(zoom in 0..MAX_ZOOM) { "Zoom must be 0..$MAX_ZOOM, got $zoom" }
            val side = 1 shl zoom
            require(x in 0 until side && y in 0 until side) { "Tile $zoom/$x/$y is outside the world" }
            return TileKey((zoom.toLong() shl ZOOM_SHIFT) or Morton.encode(x, y))
        }

        val ROOT: TileKey = of(0, 0, 0)
    }
}
