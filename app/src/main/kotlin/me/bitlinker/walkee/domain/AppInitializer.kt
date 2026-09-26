package me.bitlinker.walkee.domain

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.bitlinker.walkee.data.location.LocationRepository
import me.bitlinker.walkee.data.map.MapRepository
import me.bitlinker.walkee.data.settings.SettingsRepository
import me.bitlinker.walkee.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/** Process start-up: load persisted fog and resume tracking if it was left on. */
@Singleton
class AppInitializer @Inject constructor(
    private val mapRepository: MapRepository,
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
    private val trackingSession: TrackingSession,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            try {
                mapRepository.load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load fog storage", e)
            }
            val settings = settingsRepository.settings.first()
            if (settings.trackingEnabled && locationRepository.hasPermission.value) {
                trackingSession.setEnabled(true)
            }
        }
    }

    private companion object {
        const val TAG = "AppInitializer"
    }
}
