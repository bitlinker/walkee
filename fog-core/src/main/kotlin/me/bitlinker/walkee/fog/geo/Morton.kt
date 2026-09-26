package me.bitlinker.walkee.fog.geo

/**
 * Morton (Z-order) code: interleaves the bits of `x` (even positions) and `y` (odd positions).
 *
 * Supports coordinates up to 29 bits, producing a 58-bit code. Interleaving gives the quadkey
 * property: the code of a tile's parent is its own code shifted right by two bits, so all
 * descendants of a tile occupy one contiguous range of codes.
 */
internal object Morton {
    fun encode(x: Int, y: Int): Long = spread(x) or (spread(y) shl 1)

    fun decodeX(code: Long): Int = compact(code)

    fun decodeY(code: Long): Int = compact(code ushr 1)

    private fun spread(value: Int): Long {
        var v = value.toLong() and 0xFFFF_FFFFL
        v = (v or (v shl 16)) and 0x0000FFFF0000FFFFL
        v = (v or (v shl 8)) and 0x00FF00FF00FF00FFL
        v = (v or (v shl 4)) and 0x0F0F0F0F0F0F0F0FL
        v = (v or (v shl 2)) and 0x3333333333333333L
        v = (v or (v shl 1)) and 0x5555555555555555L
        return v
    }

    private fun compact(code: Long): Int {
        var v = code and 0x5555555555555555L
        v = (v or (v ushr 1)) and 0x3333333333333333L
        v = (v or (v ushr 2)) and 0x0F0F0F0F0F0F0F0FL
        v = (v or (v ushr 4)) and 0x00FF00FF00FF00FFL
        v = (v or (v ushr 8)) and 0x0000FFFF0000FFFFL
        v = (v or (v ushr 16)) and 0x00000000FFFFFFFFL
        return v.toInt()
    }
}
