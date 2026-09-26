package me.bitlinker.walkee.fog.geo

/**
 * A hexagon of a [HexLattice]: row and column packed into one `Long` (row in the high 32 bits).
 * Columns are not wrapped, so windows that cross the antimeridian keep consecutive indices; the
 * same hexagon also has column `col ± lattice.columns`.
 */
@JvmInline
value class HexKey(val packed: Long) {

    val row: Int get() = (packed shr 32).toInt()

    val col: Int get() = packed.toInt()

    override fun toString(): String = "hex($row, $col)"

    companion object {
        fun of(row: Int, col: Int): HexKey = HexKey((row.toLong() shl 32) or (col.toLong() and 0xFFFFFFFFL))
    }
}
