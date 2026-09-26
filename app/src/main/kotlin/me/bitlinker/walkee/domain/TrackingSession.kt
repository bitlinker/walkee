package me.bitlinker.walkee.domain

import android.util.Log
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

/**
 * The core game loop: while enabled, turns accepted location fixes into revealed fog cells.
 * Lives in the application scope so it keeps running while the user switches screens.
 */
@Singleton
class TrackingSession @Inject constructor(
    private val locationRepository: LocationRepository,
    private val mapRepository: MapRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private var job: Job? = null

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (enabled == (job != null)) return
        if (enabled) {
            job = scope.launch { track() }
        } else {
            job?.cancel()
            job = null
        }
        _isTracking.value = enabled
    }

    private suspend fun track() {
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

    /** The brush follows GPS uncertainty but stays within sensible bounds. */
    private fun brushRadiusMetres(fix: LocationFix): Double =
        fix.accuracyMetres.toDouble().coerceIn(MIN_BRUSH_METRES, MAX_BRUSH_METRES)

    private companion object {
        const val TAG = "TrackingSession"
        const val MIN_BRUSH_METRES = 12.0
        const val MAX_BRUSH_METRES = 35.0
        /** Longer gaps between fixes are not connected: the path in between is unknown. */
        const val MAX_STROKE_METRES = 150.0
    }
}
