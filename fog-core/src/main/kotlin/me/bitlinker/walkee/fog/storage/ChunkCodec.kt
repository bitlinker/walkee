package me.bitlinker.walkee.fog.storage

import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.TileKey
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Binary chunk format (ADR 0002). All integers big-endian (`DataOutput`), bitmap words little-endian.
 *
 * ```
 * u32  magic "WKFC"
 * u8   format version (1)
 * i64  packed TileKey of the chunk
 * i32  visited cell count
 * u8   state: 0 = PARTIAL (bitmap follows), 1 = FULL (no bitmap)
 * i32  compressed bitmap length      | only for PARTIAL
 * u8[] deflate(bitmap, 8192 bytes)   | only for PARTIAL
 * ```
 */
object ChunkCodec {
    private const val MAGIC = 0x574B4643 // "WKFC"
    private const val VERSION = 1
    private const val STATE_PARTIAL = 0
    private const val STATE_FULL = 1
    private const val BITMAP_BYTES = Chunk.WORDS * Long.SIZE_BYTES

    fun encode(chunk: Chunk): ByteArray {
        val out = ByteArrayOutputStream(if (chunk.isFull) 32 else 2048)
        DataOutputStream(out).use { data ->
            data.writeInt(MAGIC)
            data.writeByte(VERSION)
            data.writeLong(chunk.key.packed)
            data.writeInt(chunk.visitedCount)
            if (chunk.isFull) {
                data.writeByte(STATE_FULL)
            } else {
                data.writeByte(STATE_PARTIAL)
                val compressed = deflate(wordsToBytes(chunk.copyWords()))
                data.writeInt(compressed.size)
                data.write(compressed)
            }
        }
        return out.toByteArray()
    }

    /** @throws IOException on malformed input, including a bitmap that disagrees with the stored count. */
    fun decode(bytes: ByteArray): Chunk {
        DataInputStream(bytes.inputStream()).use { data ->
            val magic = data.readInt()
            if (magic != MAGIC) throw IOException("Not a chunk file (magic 0x${magic.toString(16)})")
            val version = data.readUnsignedByte()
            if (version != VERSION) throw IOException("Unsupported chunk format version $version")
            val key = TileKey(data.readLong())
            if (key.zoom != FogGrid.CHUNK_ZOOM) throw IOException("Chunk key $key is not at zoom ${FogGrid.CHUNK_ZOOM}")
            val visitedCount = data.readInt()
            val chunk = when (val state = data.readUnsignedByte()) {
                STATE_FULL -> Chunk.full(key)
                STATE_PARTIAL -> {
                    val length = data.readInt()
                    if (length <= 0 || length > bytes.size) throw IOException("Bad compressed length $length")
                    val compressed = ByteArray(length)
                    data.readFully(compressed)
                    Chunk.fromWords(key, bytesToWords(inflate(compressed)))
                }
                else -> throw IOException("Unknown chunk state $state")
            }
            if (chunk.visitedCount != visitedCount) {
                throw IOException("Chunk $key bitmap has ${chunk.visitedCount} bits set, header says $visitedCount")
            }
            return chunk
        }
    }

    private fun wordsToBytes(words: LongArray): ByteArray {
        val buffer = ByteBuffer.allocate(BITMAP_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asLongBuffer().put(words)
        return buffer.array()
    }

    private fun bytesToWords(bytes: ByteArray): LongArray {
        val words = LongArray(Chunk.WORDS)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get(words)
        return words
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        try {
            deflater.setInput(input)
            deflater.finish()
            val out = ByteArrayOutputStream(input.size / 4)
            val buffer = ByteArray(4096)
            while (!deflater.finished()) {
                val n = deflater.deflate(buffer)
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(input: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(input)
            val output = ByteArray(BITMAP_BYTES)
            var offset = 0
            while (offset < output.size) {
                val n = inflater.inflate(output, offset, output.size - offset)
                if (n == 0 && (inflater.finished() || inflater.needsInput())) break
                offset += n
            }
            if (offset != output.size || !inflater.finished()) {
                throw IOException("Chunk bitmap is $offset bytes, expected $BITMAP_BYTES")
            }
            return output
        } catch (e: DataFormatException) {
            throw IOException("Corrupt chunk bitmap", e)
        } finally {
            inflater.end()
        }
    }
}
