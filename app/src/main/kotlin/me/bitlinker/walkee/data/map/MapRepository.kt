package me.bitlinker.walkee.data.map

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.bitlinker.walkee.di.ApplicationScope
import me.bitlinker.walkee.di.DefaultDispatcher
import me.bitlinker.walkee.di.IoDispatcher
import me.bitlinker.walkee.fog.geo.GeoPoint
import me.bitlinker.walkee.fog.geo.TileKey
import me.bitlinker.walkee.fog.storage.ChangeSet
import me.bitlinker.walkee.fog.storage.MapStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/** Tells renderers which tiles went stale. */
sealed interface FogInvalidation {
    /** Everything: after loading from disk. */
    data object All : FogInvalidation

    data class Chunks(val chunks: Set<TileKey>) : FogInvalidation
}

data class FogProgress(val visitedCells: Long)

/**
 * High-level fog operations on top of [MapStorage] (ADR 0004): brushes and strokes from
 * positions, coverage grids for tile rendering, progress, and disk persistence scheduling.
 */
@Singleton
class MapRepository @Inject constructor(
    private val storage: MapStorage,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val loadMutex = Mutex()
    private var loaded = false

    private val _progress = MutableStateFlow(FogProgress(0))
    val progress: StateFlow<FogProgress> = _progress.asStateFlow()

    private val fullInvalidations = MutableSharedFlow<FogInvalidation>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Stale-tile notifications for the fog layer renderer. */
    val invalidations: Flow<FogInvalidation> = merge(
        fullInvalidations,
        storage.changes.map { FogInvalidation.Chunks(it.chunks) },
    )

    /** Loads persisted chunks once and starts write-behind flushing. Safe to call repeatedly. */
    suspend fun load() {
        loadMutex.withLock {
            if (loaded) return
            val result = withContext(ioDispatcher) { storage.load() }
            for (failure in result.failures) {
                Log.w(TAG, "Skipped corrupt chunk ${failure.source}", failure.error)
            }
            loaded = true
            publishProgress()
            fullInvalidations.tryEmit(FogInvalidation.All)
            startAutoFlush()
            Log.i(TAG, "Loaded ${result.chunks.size} chunks, ${storage.visitedCellCount} visited cells")
        }
    }

    /** Reveals a disk around [center]. */
    suspend fun paintDisk(center: GeoPoint, radiusMetres: Double): ChangeSet = withContext(defaultDispatcher) {
        record(storage.markVisited(FogBrush.diskCells(center, radiusMetres)))
    }

    /** Reveals a thick line between two consecutive positions. */
    suspend fun paintStroke(from: GeoPoint, to: GeoPoint, radiusMetres: Double): ChangeSet = withContext(defaultDispatcher) {
        record(storage.markVisited(FogBrush.strokeCells(from, to, radiusMetres)))
    }

    /** Coverage grid for a map tile. Synchronous and thread-safe: called from MapKit worker threads. */
    fun coverage(tile: TileKey, displayZoom: Int): FogCoverage = FogCoverageBuilder.build(storage, tile, displayZoom)

    /** Writes unsaved chunks now (e.g. when the app goes to background). */
    suspend fun flush() {
        withContext(ioDispatcher) {
            val written = storage.flush()
            if (written > 0) Log.d(TAG, "Flushed $written chunks")
        }
    }

    private fun record(change: ChangeSet): ChangeSet {
        if (!change.isEmpty) publishProgress()
        return change
    }

    private fun publishProgress() {
        _progress.value = FogProgress(storage.visitedCellCount)
    }

    @OptIn(FlowPreview::class)
    private fun startAutoFlush() {
        scope.launch {
            storage.changes.debounce(FLUSH_DELAY).collect {
                try {
                    flush()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist fog chunks; will retry after the next change", e)
                }
            }
        }
    }

    private companion object {
        const val TAG = "MapRepository"
        val FLUSH_DELAY = 5.seconds
    }
}
