package me.bitlinker.walkee.ui.map

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Minimal PNG encoder for palette images (colour type 3, 8 bits per pixel) with per-entry alpha.
 *
 * Fog tiles contain at most a few distinct ARGB colours, so a palette image is several times
 * smaller than RGBA and, with fast deflate over long runs of equal bytes, much quicker to produce
 * than `Bitmap.compress` (ADR 0003). Pure Kotlin: tested on the JVM.
 */
object IndexedPngEncoder {
    private const val MAX_PALETTE = 256
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)

    /**
     * Encodes non-premultiplied ARGB [pixels] (row-major, `width × height`).
     *
     * @throws IllegalArgumentException if the image has more than 256 distinct colours.
     */
    fun encode(pixels: IntArray, width: Int, height: Int): ByteArray {
        require(pixels.size == width * height) { "Expected ${width * height} pixels, got ${pixels.size}" }

        // Palette in order of first appearance; fully transparent pixels share one entry.
        val palette = IntArray(MAX_PALETTE)
        var paletteSize = 0
        val indexOf = HashMap<Int, Int>()
        // Each row is prefixed with filter type 0 (None).
        val scanlines = ByteArray(height * (width + 1))
        var lastColor = 0
        var lastIndex = -1
        var out = 0
        for (y in 0 until height) {
            scanlines[out++] = 0
            val rowStart = y * width
            for (x in 0 until width) {
                val argb = pixels[rowStart + x].let { if (it ushr 24 == 0) 0 else it }
                val index = if (argb == lastColor && lastIndex >= 0) {
                    lastIndex
                } else {
                    indexOf.getOrPut(argb) {
                        require(paletteSize < MAX_PALETTE) { "Image has more than $MAX_PALETTE colours" }
                        palette[paletteSize] = argb
                        paletteSize++
                        paletteSize - 1
                    }.also {
                        lastColor = argb
                        lastIndex = it
                    }
                }
                scanlines[out++] = index.toByte()
            }
        }

        val png = ByteArrayOutputStream(1024)
        png.write(SIGNATURE)
        png.chunk("IHDR", ByteArray(13).also {
            it.putInt(0, width)
            it.putInt(4, height)
            it[8] = 8 // bit depth
            it[9] = 3 // colour type: palette
            it[10] = 0 // compression
            it[11] = 0 // filter
            it[12] = 0 // interlace
        })
        png.chunk("PLTE", ByteArray(paletteSize * 3).also {
            for (i in 0 until paletteSize) {
                val c = palette[i]
                it[i * 3] = (c ushr 16).toByte()
                it[i * 3 + 1] = (c ushr 8).toByte()
                it[i * 3 + 2] = c.toByte()
            }
        })
        png.chunk("tRNS", ByteArray(paletteSize) { (palette[it] ushr 24).toByte() })
        png.chunk("IDAT", deflate(scanlines))
        png.chunk("IEND", ByteArray(0))
        return png.toByteArray()
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_SPEED)
        try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream(data.size / 16 + 64)
            val buffer = ByteArray(8192)
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer))
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun ByteArrayOutputStream.chunk(type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        write(ByteArray(4).also { it.putInt(0, data.size) })
        write(typeBytes)
        write(data)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        write(ByteArray(4).also { it.putInt(0, crc.value.toInt()) })
    }

    private fun ByteArray.putInt(offset: Int, value: Int) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
    }
}
