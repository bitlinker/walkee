package me.bitlinker.walkee.fog.storage

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Durable storage of chunks. Implementations are blocking; callers pick the thread. */
interface ChunkStore {
    /** Loads every stored chunk. Unreadable chunks are skipped and reported in [LoadResult.failures]. */
    fun loadAll(): LoadResult

    /** Persists the given snapshots, replacing earlier versions of the same chunks. */
    fun save(chunks: Collection<Chunk>)

    /** Removes every stored chunk. */
    fun deleteAll()

    data class LoadResult(val chunks: List<Chunk>, val failures: List<Failure>) {
        data class Failure(val source: String, val error: Exception)
    }
}

/** Keeps chunks in memory only; for tests and previews. */
class InMemoryChunkStore : ChunkStore {
    private val chunks = LinkedHashMap<Long, Chunk>()

    override fun loadAll(): ChunkStore.LoadResult = synchronized(chunks) {
        ChunkStore.LoadResult(chunks.values.toList(), emptyList())
    }

    override fun save(chunks: Collection<Chunk>) = synchronized(this.chunks) {
        for (chunk in chunks) this.chunks[chunk.key.packed] = chunk
    }

    override fun deleteAll() = synchronized(chunks) { chunks.clear() }

    val size: Int get() = synchronized(chunks) { chunks.size }
}

/**
 * One file per chunk in [directory]: `<packed key as 16 hex digits>.chunk` in [ChunkCodec] format.
 * Writes go to a temp file and are moved into place, so a crash never leaves a half-written chunk.
 * Files that fail to decode are renamed to `*.corrupt` and reported instead of aborting the load.
 */
class FileChunkStore(private val directory: File) : ChunkStore {

    override fun loadAll(): ChunkStore.LoadResult {
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(CHUNK_EXTENSION) }
            ?: return ChunkStore.LoadResult(emptyList(), emptyList())
        val chunks = ArrayList<Chunk>(files.size)
        val failures = ArrayList<ChunkStore.LoadResult.Failure>()
        for (file in files.sortedBy { it.name }) {
            try {
                chunks += ChunkCodec.decode(file.readBytes())
            } catch (e: IOException) {
                failures += ChunkStore.LoadResult.Failure(file.name, e)
                file.renameTo(File(directory, file.name + CORRUPT_SUFFIX))
            }
        }
        return ChunkStore.LoadResult(chunks, failures)
    }

    override fun save(chunks: Collection<Chunk>) {
        if (chunks.isEmpty()) return
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create chunk directory $directory")
        }
        for (chunk in chunks) {
            val target = File(directory, fileName(chunk))
            val temp = File(directory, target.name + TEMP_SUFFIX)
            temp.writeBytes(ChunkCodec.encode(chunk))
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    /** Deletes chunk files, including leftover temp files and quarantined corrupt ones. */
    override fun deleteAll() {
        val files = directory.listFiles { file -> file.isFile && CHUNK_EXTENSION in file.name } ?: return
        for (file in files) {
            if (!file.delete() && file.exists()) throw IOException("Cannot delete $file")
        }
    }

    private fun fileName(chunk: Chunk): String = "%016x$CHUNK_EXTENSION".format(chunk.key.packed)

    private companion object {
        const val CHUNK_EXTENSION = ".chunk"
        const val TEMP_SUFFIX = ".tmp"
        const val CORRUPT_SUFFIX = ".corrupt"
    }
}
