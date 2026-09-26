package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.map.HexCoverage
import me.bitlinker.walkee.data.map.HexCoverageBuilder
import me.bitlinker.walkee.data.settings.FogStyle
import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.HexLattice
import me.bitlinker.walkee.fog.geo.TileKey
import me.bitlinker.walkee.fog.storage.InMemoryChunkStore
import me.bitlinker.walkee.fog.storage.MapStorage
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.random.Random

class HexFogTilePainterTest {

    private val style = FogStyle(opacity = 1f, colorRgb = 0x112233, revealedOpacity = 0.2f, revealedColorRgb = 0xFFD600)
    private val size = FogTilePainter.TILE_SIZE
    private val fog = 0xFF112233.toInt()
    private val tint = (51 shl 24) or 0xFFD600 // 0.2 * 255 = 51
    private val lattice = HexLattice(18)
    private val storage = MapStorage(InMemoryChunkStore())

    /** Moscow's centre (see FogGridTest). */
    private val cell = cell(1_267_712, 657_424)

    private fun cell(x: Int, y: Int) = TileKey.of(FogGrid.STORAGE_ZOOM, x, y)

    @ParameterizedTest(name = "tile zoom {0}")
    @ValueSource(ints = [12, 14, 16, 17, 18, 20, 21, 23])
    fun `every pixel equals its 4×4 supersampled open share`(zoom: Int) {
        // ~1.25 % of cells: about half of the 56-cell hexagons open.
        val random = Random(zoom)
        storage.markVisited(List(8000) { cell(cell.x - 400 + random.nextInt(800), cell.y - 400 + random.nextInt(800)) })
        val coverage = mixedCoverageNear(tileAt(zoom))

        val pixels = HexFogTilePainter.paint(coverage, style)
        val expected = supersampledReference(coverage)
        assertArrayEquals(expected, pixels)
        assertTrue(pixels.toSet().size <= 17)
        IndexedPngEncoder.encode(pixels, size, size)
    }

    @Test
    fun `a single open hexagon is tinted inside, fog outside, antialiased along its edges`() {
        val middle = cell(cell.x + 16, cell.y) // 16 cells into its z16 tile both ways
        storage.markVisited(listOf(middle))
        val tile = middle.ancestor(16) // 32 cells, so hexagons are 64 px wide
        val tileSide = 1 shl (FogGrid.STORAGE_ZOOM - 16)
        val coverage = HexCoverageBuilder.build(storage, tile, lattice)
        val pixels = HexFogTilePainter.paint(coverage, style)

        val hex = lattice.hexAt(middle.x + 0.5, middle.y + 0.5)
        val pixelSide = tileSide.toDouble() / size
        fun pixelOf(x: Double, y: Double) =
            ((y - tile.y * tileSide) / pixelSide).toInt() * size + ((x - tile.x * tileSide) / pixelSide).toInt()
        val centreX = lattice.centerX(hex.row, hex.col)
        val centreY = lattice.centerY(hex.row)
        assertEquals(tint, pixels[pixelOf(centreX, centreY)])
        assertEquals(fog, pixels[pixelOf(centreX + lattice.width, centreY)])
        assertEquals(fog, pixels[pixelOf(centreX, centreY + lattice.rowSpacing)])

        // Colours strictly between fog and tint occur only along the hexagon's outline.
        val mixed = pixels.count { it != fog && it != tint }
        assertTrue(mixed in 1..400, "mixed pixels: $mixed")
    }

    @Test
    fun `hexagons are drawn while at least 4 px wide`() {
        assertTrue(HexFogTilePainter.drawsHexagons(tileZoom = 12, displayZoom = 18))
        assertFalse(HexFogTilePainter.drawsHexagons(tileZoom = 11, displayZoom = 18))
        assertTrue(HexFogTilePainter.drawsHexagons(tileZoom = 10, displayZoom = 16))
        assertTrue(HexFogTilePainter.drawsHexagons(tileZoom = 23, displayZoom = 19))
    }

    /** The first tile around [start] that shows both open and closed hexagons. */
    private fun mixedCoverageNear(start: TileKey): HexCoverage {
        for (dy in 0..16) for (dx in 0..16) {
            val coverage = HexCoverageBuilder.build(storage, TileKey.of(start.zoom, start.x + dx, start.y + dy), lattice)
            if (!coverage.isAllOpen && !coverage.isAllClosed) return coverage
        }
        throw AssertionError("No mixed tile near $start")
    }

    private fun tileAt(zoom: Int): TileKey {
        if (zoom <= FogGrid.STORAGE_ZOOM) return cell.ancestor(zoom)
        var tile = cell
        while (tile.zoom < zoom) tile = tile.child(3)
        return tile
    }

    /** Every pixel sampled 4×4, without the painter's shortcut for pixels far from edges. */
    private fun supersampledReference(coverage: HexCoverage): IntArray {
        val tileSide = Math.scalb(1.0, FogGrid.STORAGE_ZOOM - coverage.tile.zoom)
        val pixelSide = tileSide / size
        val palette = IntArray(17) { FogPalette.colorOf(1f - it / 16f, style) }
        return IntArray(size * size) { index ->
            val px = index % size
            val py = index / size
            var open = 0
            for (sy in 0 until 4) for (sx in 0 until 4) {
                val x = coverage.tile.x * tileSide + (px + (sx + 0.5) / 4) * pixelSide
                val y = coverage.tile.y * tileSide + (py + (sy + 0.5) / 4) * pixelSide
                if (coverage.isOpen(lattice.hexAt(x, y))) open++
            }
            palette[open]
        }
    }
}
