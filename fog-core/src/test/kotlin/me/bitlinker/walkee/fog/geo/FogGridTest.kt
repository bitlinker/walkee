package me.bitlinker.walkee.fog.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FogGridTest {

    private val moscow = GeoPoint(55.7558, 37.6173)

    @Test
    fun `Moscow centre lands in the expected tiles`() {
        assertEquals(TileKey.of(13, 4952, 2568), FogGrid.tileAt(moscow, 13))
        assertEquals(TileKey.of(18, 158_464, 82_178), FogGrid.tileAt(moscow, 18))
        assertEquals(TileKey.of(21, 1_267_712, 657_424), FogGrid.cellAt(moscow))
        assertEquals(FogGrid.tileAt(moscow, 13), FogGrid.chunkOf(FogGrid.cellAt(moscow)))
    }

    @Test
    fun `cell and local index round trip`() {
        val cell = FogGrid.cellAt(moscow)
        val chunk = FogGrid.chunkOf(cell)
        val index = FogGrid.localIndex(cell)
        assertEquals((657_424 and 255) * 256 + (1_267_712 and 255), index)
        assertEquals(cell, FogGrid.cellOf(chunk, index))

        assertEquals(TileKey.of(21, 4952 shl 8, 2568 shl 8), FogGrid.cellOf(chunk, 0))
        assertEquals(TileKey.of(21, (4952 shl 8) + 255, (2568 shl 8) + 255), FogGrid.cellOf(chunk, FogGrid.CHUNK_CELLS - 1))
    }

    @Test
    fun `local index rejects non-cells`() {
        assertThrows(IllegalArgumentException::class.java) { FogGrid.localIndex(TileKey.of(18, 0, 0)) }
        assertThrows(IllegalArgumentException::class.java) { FogGrid.cellOf(TileKey.of(12, 0, 0), 0) }
    }

    @Test
    fun `tileAt clamps the south-east edge into the last tile`() {
        assertEquals(TileKey.of(5, 31, 31), FogGrid.tileAt(WorldPoint(1.0, 1.0), 5))
        assertEquals(TileKey.of(5, 0, 0), FogGrid.tileAt(WorldPoint(0.0, 0.0), 5))
        assertEquals(TileKey.ROOT, FogGrid.tileAt(WorldPoint(0.999, 0.001), 0))
    }

    @Test
    fun `tile origin and centre are consistent with tileAt`() {
        val tile = TileKey.of(18, 158_464, 82_178)
        val origin = FogGrid.tileOrigin(tile)
        assertEquals(tile, FogGrid.tileAt(WorldPoint(origin.x + 1e-9, origin.y + 1e-9), 18))
        assertEquals(tile, FogGrid.tileAt(FogGrid.tileCenter(tile), 18))
    }

    @Test
    fun `storage cell in Moscow is about 11 metres`() {
        assertEquals(10.78, FogGrid.cellSizeMetres(moscow.latitude), 0.01)
    }
}
