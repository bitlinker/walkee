package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.map.FogCoverage

/**
 * Turns a [FogCoverage] grid into the ARGB pixels of one [TILE_SIZE] px tile, with colours from a
 * [FogPalette]. Implementations are pure — no Android types — so they are unit-tested on the JVM,
 * and thread-safe: MapKit requests tiles concurrently. PNG encoding happens in
 * [MapFogLayerRenderer] (ADR 0003).
 */
interface FogTilePainter {
    /**
     * Cells of context the painter needs on every side of a tile at [tileZoom] (see
     * [FogCoverage.margin]); `0` when it looks only at the tile's own cells.
     */
    fun margin(tileZoom: Int, displayZoom: Int): Int

    fun paint(coverage: FogCoverage, palette: FogPalette): IntArray

    companion object {
        const val TILE_SIZE = 256

        /** A tile of one colour. */
        fun uniform(color: Int): IntArray = IntArray(TILE_SIZE * TILE_SIZE) { color }
    }
}
