package me.bitlinker.walkee.fog.geo

/**
 * Position on the projected "world square" normalized to `[0, 1]` on both axes.
 *
 * `(0, 0)` is the north-west corner (lon −180°, lat [Epsg3395.MAX_LATITUDE]), `(1, 1)` the
 * south-east one. At zoom `z` the world is `2^z` tiles wide, so the tile containing a point is
 * `floor(x · 2^z), floor(y · 2^z)` and the global pixel is `x · 2^(z+8)` for 256 px tiles.
 */
data class WorldPoint(val x: Double, val y: Double)
