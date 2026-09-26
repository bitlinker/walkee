package me.bitlinker.walkee.domain

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.bitlinker.walkee.data.location.GeoDistance
import me.bitlinker.walkee.data.location.LocationFix
import me.bitlinker.walkee.data.location.LocationRepository
import me.bitlinker.walkee.data.map.MapRepository
import me.bitlinker.walkee.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

/**
 * The core game loop: while on, turns accepted location fixes into revealed fog cells.
 * Lives in the application scope; a foreground service keeps the process alive meanwhile, so it
 * goes on with the screen off (ADR 0006).
 */
@Singleton
class TrackingSession @Inject constructor(
    private val locationRepository: LocationRepository,
    private val mapRepository: MapRepository,
    private val foreground: TrackingForeground,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private var job: Job? = null

    /**
     * Starts revealing the map and brings up the foreground service. Returns false, leaving
     * tracking off, when the system does not allow the service right now.
     */
    @Synchronized
    fun start(): Boolean {
        if (job != null) return true
        // On before the service starts: the service follows isTracking and would stop at once.
        job = scope.launch { track() }
        _isTracking.value = true
        if (foreground.start()) return true
        stop()
        return false
    }

    /**
     * Stops revealing the map and saves what was revealed (asynchronously); the foreground service
     * follows [isTracking] and stops as well.
     */
    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        _isTracking.value = false
    }

    private suspend fun track() {
        // A process started in the background (auto-start) may get here before AppInitializer has
        // loaded the fog, and loading replaces whatever was painted before it.
        mapRepository.load()
        // The repository saves after a pause in changes, which never comes while walking new
        // ground; the process may be killed at any moment in the background.
        runSavingPeriodically(SAVE_INTERVAL, ::save) {
            var previous: LocationFix? = null
            locationRepository.fixes.collect { fix ->
                val radius = brushRadiusMetres(fix)
                val last = previous
                val change = if (last != null && GeoDistance.metres(last.point, fix.point) <= MAX_STROKE_METRES) {
                    mapRepository.paintStroke(last.point, fix.point, radius)
                } else {
                    mapRepository.paintDisk(fix.point, radius)
                }
                if (!change.isEmpty) Log.d(TAG, "Revealed ${change.addedCells} cells")
                previous = fix
            }
        }
    }

    private suspend fun save() {
        try {
            mapRepository.flush()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save revealed cells", e)
        }
    }

    /** The brush follows GPS uncertainty but stays within sensible bounds. */
    private fun brushRadiusMetres(fix: LocationFix): Double =
        fix.accuracyMetres.toDouble().coerceIn(MIN_BRUSH_METRES, MAX_BRUSH_METRES)

    private companion object {
        const val TAG = "TrackingSession"
        const val MIN_BRUSH_METRES = 12.0
        const val MAX_BRUSH_METRES = 35.0
        /** Longer gaps between fixes are not connected: the path in between is unknown. */
        const val MAX_STROKE_METRES = 150.0
        /** At most this much of a walk is lost if the process dies; also saved when tracking stops. */
        val SAVE_INTERVAL = 5.minutes
    }
}
