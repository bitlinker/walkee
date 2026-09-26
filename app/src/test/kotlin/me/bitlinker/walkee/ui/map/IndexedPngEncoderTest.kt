package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.map.FogCoverage
import me.bitlinker.walkee.data.settings.FogStyle
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.random.Random

class IndexedPngEncoderTest {

    private val size = FogTilePainter.TILE_SIZE

    @Test
    fun `painted fog tile decodes back to the same pixels`() {
        val random = Random(5)
        val counts = IntArray(64 * 64) { random.nextInt(0, 5) }
        val pixels = SquareFogTilePainter.paint(FogCoverage(64, counts, capacity = 4), FogPalette(FogStyle(opacity = 0.85f)))

        val png = IndexedPngEncoder.encode(pixels, size, size)
        assertArrayEquals(pixels, decode(png))
    }

    @Test
    fun `soft cloudy tile fits the palette and decodes back`() {
        val random = Random(9)
        val counts = IntArray(12 * 12) { if (random.nextInt(3) == 0) 1 else 0 }
        val coverage = FogCoverage(8, counts, capacity = 1, margin = 2, originX = 1000, originY = 2000)
        val pixels = SoftFogTilePainter(noise = true).paint(coverage, FogPalette(FogStyle()))

        val png = IndexedPngEncoder.encode(pixels, size, size)
        assertArrayEquals(pixels, decode(png))
    }

    @Test
    fun `solid and transparent tiles are tiny`() {
        val solidPixels = FogTilePainter.uniform(FogPalette(FogStyle()).hiddenColor)
        val solid = IndexedPngEncoder.encode(solidPixels, size, size)
        val transparent = IndexedPngEncoder.encode(IntArray(size * size), size, size)
        // 256 KB of RGBA would be ~1 KB even as compressed PNG; palette + deflate gets well below.
        assertTrue(solid.size < 600, "solid tile is ${solid.size} bytes")
        assertTrue(transparent.size < 600, "transparent tile is ${transparent.size} bytes")
        assertArrayEquals(solidPixels, decode(solid))
        assertTrue(decode(transparent).all { it ushr 24 == 0 })
    }

    @Test
    fun `fully transparent colours collapse into one palette entry`() {
        val pixels = intArrayOf(0x00FF0000, 0x0000FF00, 0x7F102030, 0)
        val decoded = decode(IndexedPngEncoder.encode(pixels, 2, 2))
        assertEquals(0, decoded[0] ushr 24)
        assertEquals(0, decoded[1] ushr 24)
        assertEquals(0x7F102030, decoded[2])
    }

    @Test
    fun `rejects more than 256 colours`() {
        val pixels = IntArray(300) { (0xFF shl 24) or it }
        assertThrows(IllegalArgumentException::class.java) { IndexedPngEncoder.encode(pixels, 300, 1) }
    }

    /**
     * Decodes with the JDK's reference PNG reader. `getRGB` on the indexed image resolves each
     * pixel through the palette, so colours come back exact and non-premultiplied.
     */
    private fun decode(png: ByteArray): IntArray {
        val image: BufferedImage = ImageIO.read(png.inputStream())
        assertEquals(BufferedImage.TYPE_BYTE_INDEXED, image.type)
        return IntArray(image.width * image.height).also { image.getRGB(0, 0, image.width, image.height, it, 0, image.width) }
            .map { if (it ushr 24 == 0) 0 else it }
            .toIntArray()
    }
}
