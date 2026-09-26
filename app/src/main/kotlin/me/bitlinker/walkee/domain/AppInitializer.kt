package me.bitlinker.walkee.domain

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.bitlinker.walkee.data.map.MapRepository
import me.bitlinker.walkee.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process start-up: load persisted fog. Tracking left on comes back when the app is opened
 * (`ResumeTrackingUseCase`), not here: the process may be starting in the background, where the
 * foreground service is not allowed to start (ADR 0006).
 */
@Singleton
class AppInitializer @Inject constructor(
    private val mapRepository: MapRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            try {
                mapRepository.load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load fog storage", e)
            }
        }
    }

    private companion object {
        const val TAG = "AppInitializer"
    }
}
